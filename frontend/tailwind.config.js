export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    screens: {
      sm: '560px',
      md: '768px',
      lg: '960px',
      xl: '1080px',
      '2xl': '1440px',
    },
    extend: {
      colors: {
        ink: '#102542',
        brass: '#b88c4a',
        mist: '#eef2f7',
      },
    },
  },
  plugins: [],
};
