/**
 * Same gap between every column of `table` up to `spaced`: each column gets the width of its
 * longest value, then the width left over is shared equally between them (a table left to itself
 * shares it in proportion to the content, which leaves uneven gaps). The columns after them hold
 * the buttons: they keep the width of their content, so the buttons stay where they are.
 *
 * When the values do not all fit on one line, the browser wraps the long ones first and the
 * width it leaves over is shared out the same way. When even that does not fit, the browser's
 * own layout is kept and the table scrolls.
 *
 * The table needs one <col> per column and a `table.measuring th, table.measuring td`
 * rule setting `white-space: nowrap`.
 */
export function spaceColumns(table: HTMLTableElement, spaced: number): void {
  const cols = Array.from(table.querySelectorAll('col'));
  const rows = Array.from(table.rows).filter(row => row.cells.length === cols.length);
  const available = table.parentElement!.clientWidth;
  const share = (widths: number[]) => {
    const free = available - widths.reduce((sum, width) => sum + width, 0);
    cols.forEach((col, i) => {
      col.style.width = free < 0 ? '' : `${widths[i] + (i < spaced ? free / spaced : 0)}px`;
    });
    return free >= 0;
  };

  table.classList.add('measuring');
  const oneLine = contentWidths(rows, cols.length);
  table.classList.remove('measuring');
  if (share(oneLine)) return;

  // share() has handed the layout back to the browser, which wraps the long values. A value
  // given more room afterwards may need fewer lines and take some of that room back, so the
  // widths are shared out again until they settle.
  let widths = contentWidths(rows, cols.length);
  for (let round = 0; round < 4 && share(widths); round++) {
    const settled = contentWidths(rows, cols.length);
    if (settled.every((width, i) => Math.abs(width - widths[i]) < 0.5)) return;
    widths = settled;
  }
}

/** Width of the widest value of each column, as currently laid out. */
function contentWidths(rows: HTMLTableRowElement[], count: number): number[] {
  return Array.from({ length: count }, (_, i) => Math.max(...rows.map(row => cellWidth(row.cells[i]))));
}

/**
 * Width of a cell's content, padding included. Block elements inside the cell must not
 * stretch (`width: fit-content`), or they would measure the column instead of the value.
 */
function cellWidth(cell: HTMLTableCellElement): number {
  const range = document.createRange();
  range.selectNodeContents(cell);
  const style = getComputedStyle(cell);
  return range.getBoundingClientRect().width + parseFloat(style.paddingLeft) + parseFloat(style.paddingRight);
}
