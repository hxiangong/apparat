/*
 * This file is part of Apparat.
 *
 * Copyright (C) 2010 Joa Ebert
 * http://www.joa-ebert.com/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package apparat.abc.optimization

import apparat.log.SimpleLog
import apparat.abc.{AbcMethodBody, AbcMethodParameter, AbcMethod, Abc}
import apparat.abc.utils.MethodMapper
import apparat.abc.analysis.IdenticalMethodsFinder

/**
 * @author Joa Ebert
 */
object IdenticalMethodRemoval extends (Abc => Boolean) with SimpleLog {
	//
	// NOTE: Removing methods causes a VerifyError in the Flash Player. Probably
	// one method body may not be referenced more than one time. However other
	// implementations like JITB might show a different behaviour.
	//

	override def apply(abc: Abc) = {
		if(true) {
			log.warning("Method merging causes a VerifyError in current Flash Player versions.")
			false
		} else {
			val map = IdenticalMethodsFinder(abc)

			log.debug("%d identical method(s) found.", map.size)

			val mapper = new MethodMapper(map)
			mapper.transform(abc)

			abc.methods = abc.methods filterNot { map contains _ }

			map.nonEmpty
		}
	}
}
