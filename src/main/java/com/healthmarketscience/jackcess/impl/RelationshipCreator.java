/*
Copyright (c) 2016 James Ahlborn

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.healthmarketscience.jackcess.impl;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.healthmarketscience.jackcess.IndexBuilder;
import com.healthmarketscience.jackcess.RelationshipBuilder;

/**
 *
 * @author James Ahlborn
 */
public class RelationshipCreator extends DBMutator
{
  private TableImpl _primaryTable;
  private TableImpl _secondaryTable;
  private RelationshipBuilder _relationship;
  private List<ColumnImpl> _primaryCols; 
  private List<ColumnImpl> _secondaryCols;
  private int _flags;

  public RelationshipCreator(DatabaseImpl database) 
  {
    super(database);
  }

  public TableImpl getPrimaryTable() {
    return _primaryTable;
  }

  public TableImpl getSecondaryTable() {
    return _secondaryTable;
  }

  public RelationshipImpl createRelationshipImpl(String name) {
    RelationshipImpl newRel = new RelationshipImpl(
        name, _primaryTable, _secondaryTable, _relationship.getFlags(),
        _primaryCols.size());
    newRel.getFromColumns().addAll(_primaryCols);
    newRel.getToColumns().addAll(_secondaryCols);
    return newRel;
  }

  /**
   * Creates the relationship in the database.
   * @usage _advanced_method_
   */
  public RelationshipImpl createRelationship(RelationshipBuilder relationship) 
    throws IOException 
  {
    _relationship = relationship;

    validate();

    getPageChannel().startExclusiveWrite();
    try {

      RelationshipImpl newRel = getDatabase().writeRelationship(this);

      // FIXME, handle indexes

      return newRel;

    } finally {
      getPageChannel().finishWrite();
    }
  }

  private void validate() throws IOException {

    if((_primaryTable == null) || (_secondaryTable == null)) {
      throw new IllegalArgumentException(
          "Two tables are required in relationship");
    }
    if(_primaryTable.getDatabase() != _secondaryTable.getDatabase()) {
      throw new IllegalArgumentException("Tables are not from same database");
    }

    if((_primaryCols == null) || (_primaryCols.isEmpty()) || 
       (_secondaryCols == null) || (_secondaryCols.isEmpty())) {
      throw new IllegalArgumentException("Missing columns in relationship");
    }

    if(_primaryCols.size() != _secondaryCols.size()) {
      throw new IllegalArgumentException(
          "Must have same number of columns on each side of relationship");
    }

    for(int i = 0; i < _primaryCols.size(); ++i) {
      ColumnImpl pcol = _primaryCols.get(i);
      ColumnImpl scol = _primaryCols.get(i);

      if(pcol.getType() != scol.getType()) {
        throw new IllegalArgumentException(
            "Matched columns must have the same data type");
      }
    }

    if(!_relationship.hasReferentialIntegrity()) {
      return;
    }

    

    // - same number cols
    // - cols come from right tables, tables from right db
    // - (cols can be duped in index)
    // - cols have same data types
    // - if enforce, require unique index on primary (auto-create?), index on secondary
    // - advanced, check for enforce cycles?
    // - index must be ascending

    // FIXME
  }

  private IndexBuilder createPrimaryIndex() {
    String name = getUniqueIndexName(_primaryTable);
    // FIXME?
    return createIndex(name, _primaryCols).setUnique();
  }
  
  private IndexBuilder createSecondaryIndex() {
    String name = getUniqueIndexName(_secondaryTable);
    // FIXME?

    return createIndex(name, _primaryCols);
  }
  
  private static IndexBuilder createIndex(String name, List<ColumnImpl> cols) {
    IndexBuilder idx = new IndexBuilder(name);
    for(ColumnImpl col : cols) {
      idx.addColumns(col.getName());
    }
    return idx;
  }

  private String getUniqueIndexName(TableImpl table) {
    Set<String> idxNames = TableUpdater.getIndexNames(table, null);

    boolean isPrimary = (table == _primaryTable);
    String baseName = null;
    String suffix = null;
    if(isPrimary) {
      // primary naming scheme: ".rC", ".rD", "rE" ...
      baseName = ".r";
      suffix = "C";
    } else {
      // secondary naming scheme: "<t1><t2>", "<t1><t2>1", "<t1><t2>2"
      baseName = _primaryTable.getName() + _secondaryTable.getName();
      suffix = "";
    }

    int count = 0;
    while(true) {
      String idxName = baseName + suffix;
      if(!idxNames.contains(idxName.toUpperCase())) {
        return idxName;
      }

      ++count;
      if(isPrimary) {
        char c = (char)(suffix.charAt(0) + 1);
        if(c == '[') {
          c = 'a';
        }
        suffix = "" + c;
      } else {
        suffix = "" + count;
      }      
    }    
  }
}