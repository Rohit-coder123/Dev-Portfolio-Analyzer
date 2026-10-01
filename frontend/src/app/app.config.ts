import { provideHttpClient } from "@angular/common/http";
import { ApplicationConfig } from "@angular/core";
import { provideAnimationsAsync } from "@angular/platform-browser/animations/async";
import { appRoutes } from "./app.routes";
import { provideRouter } from "@angular/router";

// Chart.js controllers/scales are registered directly in main.ts (see the
// Chart.register(...registerables) call there) rather than via ng2-charts'
// provideCharts() helper, since that provider API isn't present in every
// published ng2-charts version. Registering directly against chart.js works
// regardless of which ng2-charts version npm resolves.

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(appRoutes),
    provideHttpClient(),
    provideAnimationsAsync(), // required by Angular Material components
  ],
};
