import { bootstrapApplication } from "@angular/platform-browser";
import { Chart, registerables } from "chart.js";
import { appConfig } from "./app/app.config";
import { AppComponent } from "./app/app.component";

// Register all Chart.js controllers/elements/scales once, globally, before
// the app bootstraps. Doing this directly against chart.js (rather than via
// an ng2-charts provider helper) keeps the chart working regardless of the
// exact ng2-charts version npm resolves.
Chart.register(...registerables);

bootstrapApplication(AppComponent, appConfig).catch((err) =>
  console.error(err),
);
