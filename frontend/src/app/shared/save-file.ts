import { DownloadedFile } from '../core/services/api.service';

export function saveFile({ blob, filename }: DownloadedFile) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
