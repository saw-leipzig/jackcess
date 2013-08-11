/*
Copyright (c) 2010 James Ahlborn

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
USA

*/

package com.healthmarketscience.jackcess.util;

import com.healthmarketscience.jackcess.Table;

/**
 * Interface for handling comparisons between column values.
 *
 * @author James Ahlborn
 * @usage _intermediate_class_
 */
public interface ColumnMatcher 
{

  /**
   * Returns {@code true} if the given value1 should be considered a match for
   * the given value2 for the given column in the given table, {@code false}
   * otherwise.
   *
   * @param table the relevant table
   * @param columnName the name of the relevant column within the table
   * @param value1 the first value to match (may be {@code null})
   * @param value2 the second value to match (may be {@code null})
   */
  public boolean matches(Table table, String columnName, Object value1,
                         Object value2);
}
