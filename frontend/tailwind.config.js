/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: {
          DEFAULT: "#16213E",
          light: "#243254",
          dark: "#0E1629",
        },
        paper: "#FAFAF8",
        gold: {
          DEFAULT: "#B7912B",
          dark: "#96751F",
        },
        slate: {
          DEFAULT: "#5B6472",
        },
        hairline: "#E4E1D8",
      },
      fontFamily: {
        serif: ["'Source Serif 4'", "Georgia", "serif"],
        sans: ["'Inter'", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};
