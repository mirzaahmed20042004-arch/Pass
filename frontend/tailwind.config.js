/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        cockpit: {
          950: '#07090f',
          900: '#0c101b',
          800: '#131929',
          700: '#1c2438'
        }
      },
      animation: {
        'pulse-fast': 'pulse 0.9s cubic-bezier(0.4, 0, 0.6, 1) infinite'
      }
    }
  },
  plugins: []
};
