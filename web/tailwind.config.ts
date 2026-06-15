import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}"
  ],
  theme: {
    extend: {
      colors: {
        panel: "#101827",
        panel2: "#172033",
        line: "#263247",
        cyanSoft: "#5eead4",
        blueSoft: "#93c5fd",
        amberSoft: "#facc15",
        roseSoft: "#fb7185"
      },
      boxShadow: {
        glow: "0 18px 60px rgba(13, 148, 136, 0.16)"
      }
    }
  },
  plugins: []
};

export default config;
