import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-resume-upload',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './resume-upload.component.html',
})
export class ResumeUploadComponent {
  @Input() loading = false;
  @Input() disabledReason: string | null = null;
  @Output() fileSelected = new EventEmitter<File>();

  selectedFileName: string | null = null;
  validationError: string | null = null;

  onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file later

    if (!file) {
      return;
    }
    if (file.type !== 'application/pdf') {
      this.validationError = 'Only PDF files are supported.';
      this.selectedFileName = null;
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.validationError = 'File is larger than the 5MB limit.';
      this.selectedFileName = null;
      return;
    }

    this.validationError = null;
    this.selectedFileName = file.name;
    this.fileSelected.emit(file);
  }
}
