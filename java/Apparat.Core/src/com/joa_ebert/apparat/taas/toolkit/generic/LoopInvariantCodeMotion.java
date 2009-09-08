/*
 * This file is part of Apparat.
 * 
 * Apparat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Apparat is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Apparat. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2009 Joa Ebert
 * http://www.joa-ebert.com/
 * 
 */

package com.joa_ebert.apparat.taas.toolkit.generic;

import java.util.LinkedList;
import java.util.List;

import com.joa_ebert.apparat.abc.AbcEnvironment;
import com.joa_ebert.apparat.controlflow.ControlFlowGraphException;
import com.joa_ebert.apparat.controlflow.VertexKind;
import com.joa_ebert.apparat.controlflow.utils.SCCFinder;
import com.joa_ebert.apparat.controlflow.utils.SCComponent;
import com.joa_ebert.apparat.taas.Taas;
import com.joa_ebert.apparat.taas.TaasCode;
import com.joa_ebert.apparat.taas.TaasEdge;
import com.joa_ebert.apparat.taas.TaasException;
import com.joa_ebert.apparat.taas.TaasLocal;
import com.joa_ebert.apparat.taas.TaasMethod;
import com.joa_ebert.apparat.taas.TaasValue;
import com.joa_ebert.apparat.taas.TaasVertex;
import com.joa_ebert.apparat.taas.compiler.TaasCompiler;
import com.joa_ebert.apparat.taas.expr.AbstractBinaryExpr;
import com.joa_ebert.apparat.taas.expr.AbstractLocalExpr;
import com.joa_ebert.apparat.taas.toolkit.ITaasTool;
import com.joa_ebert.apparat.taas.toolkit.TaasToolkit;

/**
 * @author Joa Ebert
 * 
 */
public final class LoopInvariantCodeMotion implements ITaasTool
{
	private static final Taas TAAS = new Taas();

	private boolean changed;

	private boolean loopInvariant( final List<TaasVertex> vertices,
			final TaasLocal lhs, final TaasLocal rhs )
	{
		final boolean blhs = null != lhs;
		final boolean brhs = null != rhs;

		for( final TaasVertex vertex : vertices )
		{
			if( vertex.kind != VertexKind.Default )
			{
				continue;
			}

			final TaasValue value = vertex.value;

			if( value instanceof AbstractLocalExpr )
			{
				final AbstractLocalExpr localExpr = (AbstractLocalExpr)value;

				if( blhs && localExpr.local == lhs )
				{
					return false;
				}
				else if( brhs && localExpr.local == rhs )
				{
					return false;
				}
			}
		}

		return true;
	}

	public boolean manipulate( final AbcEnvironment environment,
			final TaasMethod method )
	{
		changed = false;

		try
		{
			final SCCFinder<TaasVertex, TaasEdge> sccFinder = new SCCFinder<TaasVertex, TaasEdge>();
			process( method, sccFinder.find( method.code ) );
		}
		catch( final ControlFlowGraphException exception )
		{
			throw new TaasException( exception );
		}

		if( TaasCompiler.SHOW_ALL_TRANSFORMATIONS && changed )
		{
			TaasToolkit.debug( "LoopInvariantCodeMotion", method );
		}

		return changed;
	}

	private void process( final TaasMethod method,
			final List<SCComponent<TaasVertex, TaasEdge>> sccs )
			throws ControlFlowGraphException
	{
		nextSCC: for( final SCComponent<TaasVertex, TaasEdge> scc : sccs )
		{
			final TaasVertex entry = scc.getEntry();

			if( null != entry )
			{
				final List<TaasVertex> vertices = scc.vertices;

				for( final TaasVertex vertex : vertices )
				{
					if( vertex.kind != VertexKind.Default )
					{
						continue;
					}

					final TaasValue value = vertex.value;
					final LinkedList<AbstractBinaryExpr> binExprs = new LinkedList<AbstractBinaryExpr>();

					//
					// We need all binary expressions and see if they contain
					// only loop invariants.
					//

					TaasToolkit.searchAll( value, AbstractBinaryExpr.class,
							binExprs );

					for( final AbstractBinaryExpr binExpr : binExprs )
					{
						//
						// Early out if the expression has side effects.
						//

						if( binExpr.hasSideEffects() )
						{
							continue;
						}

						//
						// Either the lhs or rhs could be an invariant.
						//

						TaasLocal lhs = null;
						TaasLocal rhs = null;

						if( binExpr.lhs instanceof TaasLocal )
						{
							lhs = (TaasLocal)binExpr.lhs;
						}

						if( binExpr.rhs instanceof TaasLocal )
						{
							rhs = (TaasLocal)binExpr.rhs;
						}

						//
						// We do nothing if the value is not an invariant.
						//

						if( !loopInvariant( vertices, lhs, rhs ) )
						{
							continue;
						}

						//
						// Now create a temporary register and put it in front
						// of the loop.
						//

						final TaasLocal local = TaasToolkit
								.createRegister( method );

						local.typeAs( binExpr.getType() );

						final TaasCode code = method.code;

						final List<TaasEdge> incommingOf = code
								.incommingOf( entry );
						TaasEdge targetEdge = null;

						for( final TaasEdge edge : incommingOf )
						{
							if( scc.contains( edge ) )
							{
								continue;
							}

							if( null != targetEdge )
							{
								continue nextSCC;
							}
							else
							{
								targetEdge = edge;
							}
						}

						//
						// Insert the vertex outside of the loop.
						// 

						final TaasVertex insertion = new TaasVertex( TAAS
								.setLocal( local, binExpr ) );

						code.add( insertion );
						targetEdge.endVertex = insertion;
						code.add( new TaasEdge( insertion, entry ) );
						TaasToolkit.replace( value, binExpr, local );

						changed = true;
					}

				}

				//
				// We need to process the subgraph of an SCC.
				//

				process( method, scc.subcomponents( entry ) );
			}
		}
	}
}
