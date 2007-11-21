// Copyright (c) 2007 Health Market Science, Inc.

package com.healthmarketscience.jackcess;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import com.healthmarketscience.jackcess.Table.RowState;
import org.apache.commons.lang.ObjectUtils;

import static com.healthmarketscience.jackcess.PageChannel.INVALID_PAGE_NUMBER;
import static com.healthmarketscience.jackcess.RowId.INVALID_ROW_NUMBER;


/**
 * Manages iteration for a Table.  Different cursors provide different methods
 * of traversing a table.  Cursors should be fairly robust in the face of
 * table modification during traversal (although depending on how the table is
 * traversed, row updates may or may not be seen).  Multiple cursors may
 * traverse the same table simultaneously.
 * <p>
 * Is not thread-safe.
 *
 * @author james
 */
public abstract class Cursor implements Iterable<Map<String, Object>>
{
  public static final int FIRST_PAGE_NUMBER = INVALID_PAGE_NUMBER;
  public static final int LAST_PAGE_NUMBER = Integer.MAX_VALUE;

  public static final RowId FIRST_ROW_ID = new RowId(
      FIRST_PAGE_NUMBER, INVALID_ROW_NUMBER);

  public static final RowId LAST_ROW_ID = new RowId(
      LAST_PAGE_NUMBER, INVALID_ROW_NUMBER);

  
  /** owning table */
  protected final Table _table;
  /** State used for reading the table rows */
  protected final RowState _rowState;
  /** the first (exclusive) row id for this iterator */
  private final RowId _firstRowId;
  /** the last (exclusive) row id for this iterator */
  private final RowId _lastRowId;
  /** the current row */
  private RowId _currentRowId;
  

  protected Cursor(Table table, RowId firstRowId, RowId lastRowId) {
    _table = table;
    _rowState = _table.createRowState();
    _firstRowId = firstRowId;
    _lastRowId = lastRowId;
    _currentRowId = firstRowId;
  }

  /**
   * Creates a normal, un-indexed cursor for the given table.
   * @param table the table over which this cursor will traverse
   */
  public static Cursor createCursor(Table table) {
    return new TableScanCursor(table);
  }

  /**
   * Convenience method for finding a specific row in a table which matches a
   * given row "pattern.  See {@link #findRow(Map)} for details on the
   * rowPattern.
   * 
   * @param table the table to search
   * @param rowPattern pattern to be used to find the row
   * @return the matching row or {@code null} if a match could not be found.
   */
  public static Map<String,Object> findRow(Table table,
                                           Map<String,Object> rowPattern)
    throws IOException
  {
    Cursor cursor = createCursor(table);
    if(cursor.findRow(rowPattern)) {
      return cursor.getCurrentRow();
    }
    return null;
  }
  
  /**
   * Convenience method for finding a specific row in a table which matches a
   * given row "pattern.  See {@link #findRow(Column,Object)} for details on
   * the pattern.
   * <p>
   * Note, a {@code null} result value is ambiguous in that it could imply no
   * match or a matching row with {@code null} for the desired value.  If
   * distinguishing this situation is important, you will need to use a Cursor
   * directly instead of this convenience method.
   * 
   * @param table the table to search
   * @param column column whose value should be returned
   * @param columnPattern column being matched by the valuePattern
   * @param valuePattern value from the columnPattern which will match the
   *                     desired row
   * @return the matching row or {@code null} if a match could not be found.
   */
  public static Object findValue(Table table, Column column,
                                 Column columnPattern, Object valuePattern)
    throws IOException
  {
    Cursor cursor = createCursor(table);
    if(cursor.findRow(columnPattern, valuePattern)) {
      return cursor.getCurrentRowValue(column);
    }
    return null;
  }
  
  public Table getTable() {
    return _table;
  }
  
  public JetFormat getFormat() {
    return getTable().getFormat();
  }

  public PageChannel getPageChannel() {
    return getTable().getPageChannel();
  }

  public RowId getCurrentRowId() {
    return _currentRowId;
  }
  
  /**
   * Returns the first row id (exclusive) as defined by this cursor.
   */
  protected RowId getFirstRowId() {
    return _firstRowId;
  }
  
  /**
   * Returns the last row id (exclusive) as defined by this cursor.
   */
  protected RowId getLastRowId() {
    return _lastRowId;
  }

  /**
   * Resets this cursor for forward iteration.  Calls {@link #beforeFirst}.
   */
  public void reset() {
    beforeFirst();
  }  

  /**
   * Resets this cursor for forward iteration (sets cursor to before the first
   * row).
   */
  public void beforeFirst() {
    reset(true);
  }
  
  /**
   * Resets this cursor for reverse iteration (sets cursor to after the last
   * row).
   */
  public void afterLast() {
    reset(false);
  }
  
  /**
   * Resets this cursor for iterating the given direction.
   */
  protected void reset(boolean moveForward) {
    _currentRowId = getDirHandler(moveForward).getBeginningRowId();
    _rowState.reset();
  }  

  /**
   * Returns {@code true} if the cursor is currently pointing at a valid row,
   * {@code false} otherwise.
   */
  public boolean isCurrentRowValid() {
    return _currentRowId.isValidRow();
  }
  
  /**
   * Calls <code>reset</code> on this table and returns a modifiable Iterator
   * which will iterate through all the rows of this table.  Use of the
   * Iterator follows the same restrictions as a call to
   * <code>getNextRow</code>.
   * @throws IllegalStateException if an IOException is thrown by one of the
   *         operations, the actual exception will be contained within
   */
  public Iterator<Map<String, Object>> iterator()
  {
    return iterator(null);
  }
  
  /**
   * Calls <code>reset</code> on this table and returns a modifiable Iterator
   * which will iterate through all the rows of this table, returning only the
   * given columns.  Use of the Iterator follows the same restrictions as a
   * call to <code>getNextRow</code>.
   * @throws IllegalStateException if an IOException is thrown by one of the
   *         operations, the actual exception will be contained within
   */
  public Iterator<Map<String, Object>> iterator(Collection<String> columnNames)
  {
    return new RowIterator(columnNames);
  }

  /**
   * Delete the current row.
   * @throws IllegalStateException if the current row is not valid (at
   *         beginning or end of table), or already deleted.
   */
  public void deleteCurrentRow() throws IOException {
    _table.deleteRow(_rowState, _currentRowId);
  }

  /**
   * Moves to the next row in the table and returns it.
   * @return The next row in this table (Column name -> Column value), or
   *         {@code null} if no next row is found
   */
  public Map<String, Object> getNextRow() throws IOException {
    return getNextRow(null);
  }

  /**
   * Moves to the next row in the table and returns it.
   * @param columnNames Only column names in this collection will be returned
   * @return The next row in this table (Column name -> Column value), or
   *         {@code null} if no next row is found
   */
  public Map<String, Object> getNextRow(Collection<String> columnNames) 
    throws IOException
  {
    return getAnotherRow(columnNames, true);
  }

  /**
   * Moves to the previous row in the table and returns it.
   * @return The previous row in this table (Column name -> Column value), or
   *         {@code null} if no previous row is found
   */
  public Map<String, Object> getPreviousRow() throws IOException {
    return getPreviousRow(null);
  }

  /**
   * Moves to the previous row in the table and returns it.
   * @param columnNames Only column names in this collection will be returned
   * @return The previous row in this table (Column name -> Column value), or
   *         {@code null} if no previous row is found
   */
  public Map<String, Object> getPreviousRow(Collection<String> columnNames) 
    throws IOException
  {
    return getAnotherRow(columnNames, false);
  }


  /**
   * Moves to another row in the table based on the given direction and
   * returns it.
   * @param columnNames Only column names in this collection will be returned
   * @return another row in this table (Column name -> Column value), where
   *         "next" may be backwards if moveForward is {@code false}, or
   *         {@code null} if there is not another row in the given direction.
   */
  private Map<String, Object> getAnotherRow(Collection<String> columnNames,
                                            boolean moveForward) 
    throws IOException
  {
    if(moveToAnotherRow(moveForward)) {
      return getCurrentRow(columnNames);
    }
    return null;
  }  

  /**
   * Moves to the next row as defined by this cursor.
   * @return {@code true} if a valid next row was found, {@code false}
   *         otherwise
   */
  public boolean moveToNextRow()
    throws IOException
  {
    return moveToAnotherRow(true);
  }

  /**
   * Moves to the previous row as defined by this cursor.
   * @return {@code true} if a valid previous row was found, {@code false}
   *         otherwise
   */
  public boolean moveToPreviousRow()
    throws IOException
  {
    return moveToAnotherRow(false);
  }

  /**
   * Moves to another row in the given direction as defined by this cursor.
   * @return {@code true} if another valid row was found in the given
   *         direction, {@code false} otherwise
   */
  private boolean moveToAnotherRow(boolean moveForward)
    throws IOException
  {
    RowId endRowId = getDirHandler(moveForward).getEndRowId();
    if(_currentRowId.equals(endRowId)) {
      // already at end
      return false;
    }
    
    _rowState.reset();
    _currentRowId = findAnotherRowId(_currentRowId, moveForward);
    return(!_currentRowId.equals(endRowId));
  }

  /**
   * Moves to the first row (as defined by the cursor) where the given column
   * has the given value.  This may be more efficient on some cursors than
   * others.  The location of the cursor when a match is not found is
   * undefined.
   *
   * @param columnPattern column from the table for this cursor which is being
   *                      matched by the valuePattern
   * @param valuePattern value which is equal to the corresponding value in
   *                     the matched row
   * @return {@code true} if a valid row was found with the given value,
   *         {@code false} if no row was found
   */
  public boolean findRow(Column columnPattern, Object valuePattern)
    throws IOException
  {
    beforeFirst();
    while(moveToNextRow()) {
      if(ObjectUtils.equals(valuePattern, getCurrentRowValue(columnPattern))) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Moves to the first row (as defined by the cursor) where the given columns
   * have the given values.  This may be more efficient on some cursors than
   * others.  The location of the cursor when a match is not found is
   * undefined.
   *
   * @param rowPattern column names and values which must be equal to the
   *                   corresponding values in the matched row
   * @return {@code true} if a valid row was found with the given values,
   *         {@code false} if no row was found
   */
  public boolean findRow(Map<String,Object> rowPattern)
    throws IOException
  {
    beforeFirst();
    Collection<String> columnNames = rowPattern.keySet();
    while(moveToNextRow()) {
      if(ObjectUtils.equals(rowPattern, getCurrentRow(columnNames))) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Skips as many rows as possible up to the given number of rows.
   * @return the number of rows skipped.
   */
  public int skipNextRows(int numRows)
    throws IOException
  {
    return skipSomeRows(numRows, true);
  }

  /**
   * Skips as many rows as possible up to the given number of rows.
   * @return the number of rows skipped.
   */
  public int skipPreviousRows(int numRows)
    throws IOException
  {
    return skipSomeRows(numRows, false);
  }

  /**
   * Skips as many rows as possible in the given direction up to the given
   * number of rows.
   * @return the number of rows skipped.
   */
  private int skipSomeRows(int numRows, boolean moveForward)
    throws IOException
  {
    int numSkippedRows = 0;
    while((numSkippedRows < numRows) && moveToAnotherRow(moveForward)) {
      ++numSkippedRows;
    }
    return numSkippedRows;
  }

  /**
   * Returns the current row in this cursor (Column name -> Column value).
   * @param columnNames Only column names in this collection will be returned
   */
  public Map<String, Object> getCurrentRow()
    throws IOException
  {
    return getCurrentRow(null);
  }

  /**
   * Returns the current row in this cursor (Column name -> Column value).
   * @param columnNames Only column names in this collection will be returned
   */
  public Map<String, Object> getCurrentRow(Collection<String> columnNames)
    throws IOException
  {
    return _table.getRow(_rowState, columnNames);
  }

  /**
   * Returns the given column from the current row.
   */
  public Object getCurrentRowValue(Column column)
    throws IOException
  {
    return _table.getRowValue(_rowState, column);
  }
  
  /**
   * Returns {@code true} if the row is marked as deleted, {@code false}
   * otherwise.  This method will not modify the rowState (it only looks at
   * the "main" row, which is where the deleted flag is located).
   */
  protected final boolean isCurrentRowDeleted()
    throws IOException
  {
    ByteBuffer rowBuffer = _rowState.getFinalPage();
    int rowNum = _rowState.getFinalRowNumber();
    
    // note, we don't use findRowStart here cause we need the unmasked value
    return Table.isDeletedRow(
        rowBuffer.getShort(Table.getRowStartOffset(rowNum, getFormat())));
  }

  /**
   * Returns the row count for the current page.  If the page number is
   * invalid or the page is not a DATA page, 0 is returned.
   */
  protected final int getRowsOnCurrentDataPage(ByteBuffer rowBuffer)
    throws IOException
  {
    int rowsOnPage = 0;
    if((rowBuffer != null) && (rowBuffer.get(0) == PageTypes.DATA)) {
      rowsOnPage = 
        rowBuffer.getShort(getFormat().OFFSET_NUM_ROWS_ON_DATA_PAGE);
    }
    return rowsOnPage;
  }

  /**
   * Finds the next non-deleted row after the given row (as defined by this
   * cursor) and returns the id of the row, where "next" may be backwards if
   * moveForward is {@code false}.  If there are no more rows, the returned
   * rowId should equal the value returned by {@link #getLastRowId} if moving
   * forward and {@link #getFirstRowId} if moving backward.
   */
  protected abstract RowId findAnotherRowId(RowId currentRowId,
                                            boolean moveForward)
    throws IOException;

  /**
   * Returns the DirHandler for the given movement direction.
   */
  protected abstract DirHandler getDirHandler(boolean moveForward);
  
  /**
   * Row iterator for this table, supports modification.
   */
  private final class RowIterator implements Iterator<Map<String, Object>>
  {
    private Collection<String> _columnNames;
    private boolean _hasNext = false;
    
    private RowIterator(Collection<String> columnNames)
    {
      try {
        reset();
        _columnNames = columnNames;
        _hasNext = moveToNextRow();
      } catch(IOException e) {
        throw new IllegalStateException(e);
      }
    }

    public boolean hasNext() { return _hasNext; }

    public void remove() {
      try {
        deleteCurrentRow();
      } catch(IOException e) {
        throw new IllegalStateException(e);
      }        
    }
    
    public Map<String, Object> next() {
      if(!hasNext()) {
        throw new NoSuchElementException();
      }
      try {
        Map<String, Object> rtn = getCurrentRow(_columnNames);
        _hasNext = moveToNextRow();
        return rtn;
      } catch(IOException e) {
        throw new IllegalStateException(e);
      }
    }
    
  }

  /**
   * Handles moving the cursor in a given direction.  Separates cursor
   * logic from value storage.
   */
  protected abstract class DirHandler
  {
    public abstract RowId getBeginningRowId();
    public abstract RowId getEndRowId();
  }

  
  /**
   * Simple un-indexed cursor.
   */
  private static class TableScanCursor extends Cursor
  {
    /** ScanDirHandler for forward iteration */
    private final ScanDirHandler _forwardDirHandler =
      new ForwardScanDirHandler();
    /** ScanDirHandler for backward iteration */
    private final ScanDirHandler _reverseDirHandler =
      new ReverseScanDirHandler();
    /** Iterator over the pages that this table owns */
    private final UsageMap.PageIterator _ownedPagesIterator;
    
    private TableScanCursor(Table table) {
      super(table, FIRST_ROW_ID, LAST_ROW_ID);
      _ownedPagesIterator = table.getOwnedPagesIterator();
    }

    @Override
    protected ScanDirHandler getDirHandler(boolean moveForward) {
      return (moveForward ? _forwardDirHandler : _reverseDirHandler);
    }
    
    @Override
    protected void reset(boolean moveForward) {
      _ownedPagesIterator.reset(moveForward);
      super.reset(moveForward);
    }

    /**
     * Position the buffer at the next row in the table
     * @return a ByteBuffer narrowed to the next row, or null if none
     */
    @Override
    protected RowId findAnotherRowId(RowId currentRowId, boolean moveForward)
      throws IOException
    {
      ScanDirHandler handler = getDirHandler(moveForward);
      
      // prepare to read next row
      _rowState.reset();
      int currentPageNumber = currentRowId.getPageNumber();
      int currentRowNumber = currentRowId.getRowNumber();

      int rowsOnPage = getRowsOnCurrentDataPage(
          _rowState.setRow(currentPageNumber, currentRowNumber));
      int rowInc = handler.getRowIncrement();
    
      // loop until we find the next valid row or run out of pages
      while(true) {

        currentRowNumber += rowInc;
        if((currentRowNumber >= 0) && (currentRowNumber < rowsOnPage)) {
          _rowState.setRow(currentPageNumber, currentRowNumber);
        } else {

          // load next page
          currentRowNumber = INVALID_ROW_NUMBER;
          currentPageNumber = handler.getAnotherPageNumber();

          ByteBuffer rowBuffer = _rowState.setRow(
              currentPageNumber, currentRowNumber);
          if(rowBuffer == null) {
            //No more owned pages.  No more rows.
            return handler.getEndRowId();
          }          

          // update row count and initial row number
          rowsOnPage = getRowsOnCurrentDataPage(rowBuffer);
          currentRowNumber = handler.getInitialRowNumber(rowsOnPage);

          // start again from the top
          continue;
        }

        if(!isCurrentRowDeleted()) {
          // we found a non-deleted row, return it
          return new RowId(currentPageNumber, currentRowNumber);
        }
      }
    }

    /**
     * Handles moving the table scan cursor in a given direction.  Separates
     * cursor logic from value storage.
     */
    private abstract class ScanDirHandler extends DirHandler {
      public abstract int getRowIncrement();
      public abstract int getAnotherPageNumber();
      public abstract int getInitialRowNumber(int rowsOnPage);
    }
    
    /**
     * Handles moving the table scan cursor forward.
     */
    private final class ForwardScanDirHandler extends ScanDirHandler {
      public RowId getBeginningRowId() {
        return getFirstRowId();
      }
      public RowId getEndRowId() {
        return getLastRowId();
      }
      public int getRowIncrement() {
        return 1;
      }
      public int getAnotherPageNumber() {
        return _ownedPagesIterator.getNextPage();
      }
      public int getInitialRowNumber(int rowsOnPage) {
        return INVALID_ROW_NUMBER;
      }
    }
    
    /**
     * Handles moving the table scan cursor backward.
     */
    private final class ReverseScanDirHandler extends ScanDirHandler {
      public RowId getBeginningRowId() {
        return getLastRowId();
      }
      public RowId getEndRowId() {
        return getFirstRowId();
      }
      public int getRowIncrement() {
        return -1;
      }
      public int getAnotherPageNumber() {
        return _ownedPagesIterator.getPreviousPage();
      }
      public int getInitialRowNumber(int rowsOnPage) {
        return rowsOnPage;
      }
    }
    
  }
  
}
