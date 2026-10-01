import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-profile-search-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './profile-search-form.component.html',
})
export class ProfileSearchFormComponent {
  /** Parent drives this so the spinner/disabled state reflect real request status. */
  @Input() loading = false;
  @Output() analyze = new EventEmitter<string>();

  readonly usernameControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^[A-Za-z0-9-]{1,39}$/)],
  });

  submit(): void {
    if (this.usernameControl.invalid || this.loading) {
      this.usernameControl.markAsTouched();
      return;
    }
    this.analyze.emit(this.usernameControl.value.trim());
  }
}
