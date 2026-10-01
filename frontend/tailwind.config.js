/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {},
  },
  // Angular Material ships its own component styles; Tailwind is used here
  // purely for layout utility classes (flex, gap, spacing) so the two don't
  // fight over base element resets.
  corePlugins: {
    preflight: false,
  },
  plugins: [],
};
