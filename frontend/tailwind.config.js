/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Prototype 6:3:1 color system: Midnight Black, Electric Blue, Sunset Orange.
        //
        // One flat ramp so every `er-*` class a page writes actually resolves.
        // 50-700 is the electric-blue ramp; 800-950 is the midnight-black family
        // that carries surfaces. Duplicate keys used to live here (500/600/700
        // were declared twice), so the later orange silently won and `er-500`
        // rendered orange instead of blue.
        er: {
          // Electric blue ramp - primary accent
          50: '#EEF3FF',
          100: '#D7E2FF',
          200: '#B3C7FF',
          300: '#8AA6FF',
          400: '#6385FF',
          500: '#2F6BFF', // primary
          600: '#2557DB',
          700: '#1C43AE',
          // Midnight black family - base surfaces
          800: '#171A22',
          900: '#0F1117',
          950: '#090A10',

          // Named aliases, because pages write `from-er-blue to-er-orange`.
          bg: '#090A10',
          bg2: '#2F6BFF',
          blue: '#2F6BFF',
          // Sunset orange - CTA accent only (6 : 3 : 1, never as body colour)
          orange: '#FF7A1A',
          // Bridge hue for the one closing gradient that references it.
          purple: '#7C5CFF',
        },
        // Neutral text ramp.
        //
        // Electric blue is the 30% accent, so prose cannot use the blue ramp —
        // `text-er-300` and `text-er-400` are #8AA6FF and #6385FF, which turned
        // every paragraph blue and collapsed the 6:3:1 ratio into "all accent".
        // Body copy and muted meta come from here instead; blue stays for links,
        // icons, focus rings and the one selected state.
        ink: {
          50: '#F4F6FB',
          100: '#E3E7F0',
          200: '#C6CCDA',
          300: '#A2AAB9', // body copy on dark  - 8.0:1 on #090A10
          400: '#7C8595', // muted meta         - 5.0:1 on #090A10
          500: '#5D6574',
        },
        campus: {
          // Prototype violet - primary brand ramp anchored at #667eea
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#667eea',  // primary
          600: '#5a67d8',
          700: '#4c51bf',
          800: '#434190',
          900: '#2e2a5c',
          950: '#1a1a2e',
        },
        accent: {
          // Prototype purple - secondary ramp anchored at #764ba2
          50: '#f5f0fa',
          100: '#ede4f7',
          200: '#dbc8ef',
          300: '#c3a3e3',
          400: '#a87bd4',
          500: '#764ba2',  // accent
          600: '#6b4199',
          700: '#5a3484',
          800: '#4a2b6b',
          900: '#3b2355',
          950: '#241438',
        },
        surface: {
          // Lavender-tinted greys to sit on the #f5f7fa canvas
          50: '#f7f8fc',
          100: '#eef0f6',
          200: '#e2e5ee',
          300: '#cbd0e0',
          400: '#9aa1b8',
          500: '#6f7690',
          600: '#545a73',
          700: '#44485e',
          800: '#2c2e3f',
          900: '#1e1f2b',
          950: '#12131a',
        },
      },
      fontFamily: {
        // Inter carries the interface; Space Grotesk carries the voice. The
        // stylesheet has always preloaded Space Grotesk but this config never
        // referenced it, so the display face silently fell back to Segoe UI.
        sans: ['Inter', "'Segoe UI'", 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Roboto', 'sans-serif'],
        display: ["'Space Grotesk'", 'Inter', "'Segoe UI'", 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'Consolas', 'monospace'],
      },
      fontSize: {
        'display-xl': ['clamp(2.5rem, 5vw, 4rem)', { lineHeight: '1.1', letterSpacing: '-0.02em', fontWeight: '700' }],
        'display-lg': ['clamp(2rem, 4vw, 3rem)', { lineHeight: '1.15', letterSpacing: '-0.015em', fontWeight: '700' }],
        'display-md': ['clamp(1.5rem, 3vw, 2.25rem)', { lineHeight: '1.2', letterSpacing: '-0.01em', fontWeight: '600' }],
        'heading-lg': ['clamp(1.25rem, 2.5vw, 1.75rem)', { lineHeight: '1.3', fontWeight: '600' }],
        'heading-md': ['clamp(1.125rem, 2vw, 1.375rem)', { lineHeight: '1.35', fontWeight: '600' }],
        'heading-sm': ['1rem', { lineHeight: '1.4', fontWeight: '600' }],
        'body-lg': ['1.125rem', { lineHeight: '1.6' }],
        'body': ['1rem', { lineHeight: '1.6' }],
        'body-sm': ['0.875rem', { lineHeight: '1.55' }],
        'caption': ['0.8125rem', { lineHeight: '1.5' }],
        'micro': ['0.75rem', { lineHeight: '1.5' }],
      },
      spacing: {
        '18': '4.5rem',
        '22': '5.5rem',
        '26': '6.5rem',
        '30': '7.5rem',
      },
      borderRadius: {
        'card': '14px',
        'pill': '9999px',
        'input': '10px',
      },
      boxShadow: {
        'card': '0 2px 10px rgba(0, 0, 0, 0.05)',
        'card-hover': '0 5px 20px rgba(0, 0, 0, 0.1)',
        'elevated': '0 20px 60px rgba(102, 126, 234, 0.25)',
        'glow': '0 5px 15px rgba(102, 126, 234, 0.4)',
        'focus': '0 0 0 3px rgba(102, 126, 234, 0.35)',
      },
      animation: {
        'fade-in': 'fadeIn 200ms ease-out',
        'slide-up': 'slideUp 250ms ease-out',
        'slide-down': 'slideDown 200ms ease-out',
        'scale-in': 'scaleIn 150ms ease-out',
        'pulse-soft': 'pulseSoft 2s ease-in-out infinite',
        'shimmer': 'shimmer 1.5s ease-in-out infinite',
        // Landing / hero orchestration
        'rise': 'rise 620ms cubic-bezier(0.16, 1, 0.3, 1) both',
        'float': 'float 6s ease-in-out infinite',
        'float-slow': 'float 9s ease-in-out infinite',
        'marquee': 'marquee 42s linear infinite',
        'drift': 'drift 18s ease-in-out infinite',
        'wiggle': 'wiggle 420ms cubic-bezier(0.34, 1.56, 0.64, 1)',
        'caret': 'caret 1.05s step-end infinite',
        'sweep': 'sweep 2.4s cubic-bezier(0.4, 0, 0.2, 1) infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideDown: {
          '0%': { opacity: '0', transform: 'translateY(-8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.96)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        pulseSoft: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.6' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        rise: {
          '0%': { opacity: '0', transform: 'translateY(20px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-12px)' },
        },
        marquee: {
          '0%': { transform: 'translateX(0)' },
          '100%': { transform: 'translateX(-50%)' },
        },
        // Slow organic drift for the hero's background field.
        drift: {
          '0%, 100%': { transform: 'translate3d(0,0,0) scale(1)' },
          '33%': { transform: 'translate3d(3%, -4%, 0) scale(1.06)' },
          '66%': { transform: 'translate3d(-3%, 3%, 0) scale(0.97)' },
        },
        wiggle: {
          '0%, 100%': { transform: 'rotate(0deg)' },
          '25%': { transform: 'rotate(-7deg)' },
          '75%': { transform: 'rotate(7deg)' },
        },
        caret: {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0' },
        },
        sweep: {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(300%)' },
        },
      },
      transitionDuration: {
        'fast': '120ms',
        'normal': '200ms',
        'slow': '300ms',
      },
      transitionTimingFunction: {
        'spring': 'cubic-bezier(0.34, 1.56, 0.64, 1)',
        'out-expo': 'cubic-bezier(0.16, 1, 0.3, 1)',
      },
    },
  },
  plugins: [],
}