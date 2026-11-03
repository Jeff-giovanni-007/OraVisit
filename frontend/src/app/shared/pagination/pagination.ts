import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  standalone: true,
  templateUrl: './pagination.html',
  styleUrl: './pagination.css',
})
export class Pagination {
  page = input.required<number>();
  size = input.required<number>();
  totalElements = input.required<number>();
  totalPages = input.required<number>();
  sizes = input<number[]>([10, 20, 50]);

  pageChange = output<number>();
  sizeChange = output<number>();

  first = computed(() => (this.totalElements() === 0 ? 0 : this.page() * this.size() + 1));
  last = computed(() => Math.min((this.page() + 1) * this.size(), this.totalElements()));

  pages = computed<(number | null)[]>(() => {
    const total = this.totalPages();
    const current = this.page();
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);

    const shown = [...new Set([0, current - 1, current, current + 1, total - 1])]
      .filter(p => p >= 0 && p < total)
      .sort((a, b) => a - b);
    const result: (number | null)[] = [];
    shown.forEach((p, i) => {
      if (i > 0 && p - shown[i - 1] > 1) result.push(null);
      result.push(p);
    });
    return result;
  });

  go(target: number) {
    if (target >= 0 && target < this.totalPages() && target !== this.page()) {
      this.pageChange.emit(target);
    }
  }

  changeSize(value: string) {
    this.sizeChange.emit(Number(value));
  }
}
