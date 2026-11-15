/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        /* ------------------------------------------------------------------ *
         * The palette: Midnight Black, Electric Blue, Sunset Orange — 7 : 4 : 1
         *
         *   950     #090A10   the page canvas        ~70% of every screen
         *   500     #2F6BFF   electric blue          ~40% of the accent budget
         *   orange  #FF7A1A   sunset orange          ~10% — one CTA per view
         *
         * The ratio moved off the landing page's 6 : 3 : 1 because blue is
         * carrying more weight now. Blue is allowed on structure as well as
         * emphasis: active nav, focus rings, links, icons, chart series,
         * selected states, hairline borders. Orange did not move — it is still
         * a button, never a surface, never a gradient, never body text.
         *
         * The ramp itself is the one the landing page already renders with and
         * is deliberately unchanged: 50-700 is the blue scale, 800-950 is the
         * midnight scale. What pages have to get right is the discipline —
         * surfaces only ever come from 800/900/950, and body copy only from
         * `ink`, never from the blue scale.
         *
         *   er-800  #171A22  raised plane: cards, panels, popovers
         *   er-900  #0F1117  recessed plane: section bands, shells
         *   er-950  #090A10  the canvas
         *
         * Contrast on #090A10 — text-er-500 (#2F6BFF) is 3.9:1, which is fine
         * for large text and icons but fails for body copy. Anything under
         * 18px uses text-er-300 (#8AA6FF, 5.6:1) or ink-300.
         *
         * (Duplicate keys used to live in this object — 500/600/700 declared
         * twice — so the later orange silently won and er-500 rendered orange.)
         * ------------------------------------------------------------------ */
        er: {
          // Electric blue — the accent scale
          50: '#EEF3FF',
          100: '#D7E2FF',
          200: '#B3C7FF',
          300: '#8AA6FF',
          400: '#6385FF',
          500: '#2F6BFF', // primary
          600: '#2557DB',
          700: '#1C43AE',
          // Midnight black — the surface scale
          800: '#171A22',
          900: '#0F1117',
          950: '#090A10',

          // Named aliases, because pages write `from-er-blue to-er-orange`.
          bg: '#090A10',
          bg2: '#0F1117',
          blue: '#2F6BFF',
          // Sunset orange — CTA only (7 : 4 : 1, never as body colour)
          orange: '#FF7A1A',
        },

        /* Neutral text ramp.
         *
         * Electric blue is the accent, so prose cannot live on the blue scale —
         * `text-er-300` and `text-er-400` are #8AA6FF and #6385FF, which turn
         * every paragraph blue and collapse the ratio into "all accent". Body
         * copy and muted meta come from here instead; blue stays for links,
         * icons, focus rings and the one selected state. */
        ink: {
          50: '#F4F6FB',
          100: '#E3E7F0', // headings
          200: '#C6CCDA', // strong body
          300: '#A2AAB9', // body copy on dark — 8.0:1 on #090A10
          400: '#7C8595', // muted meta        — 5.0:1 on #090A10
          500: '#5D6574', // faint icons, disabled — decorative only
          600: '#4A515E',
          700: '#3A4049',
          800: '#2A2F37',
          900: '#1B1F26',
          950: '#12151A',
        },

        /* ------------------------------------------------------------------ *
         * Legacy ramps, remapped.
         *
         * `campus` (violet #667eea) and `accent` (purple #764ba2) were the
         * prototype's brand scales; `surface` was a lavender grey set built for
         * a #f5f7fa canvas. All three are retired — every page writes `er-*`
         * and `ink-*` now — but the keys have to keep resolving, because a
         * stray `text-campus-950` that resolves to nothing inherits its parent
         * instead of failing loudly, and a stray violet is exactly the bug this
         * pass exists to remove.
         *
         * So each scale is *inverted* onto the new palette: its light steps
         * become midnight surfaces, its dark steps become light ink. Anything
         * still referencing them renders correctly-on-dark rather than as a
         * white block or invisible black text.
         * ------------------------------------------------------------------ */
        campus: {
          50: '#171A22',   // was the lightest tint  → raised plane
          100: '#10141C',  // was a soft chip fill   → recessed plane
          200: '#1C212B',
          300: '#262C38',
          400: '#3A4252',
          500: '#2F6BFF',  // was the violet primary → electric blue
          600: '#8AA6FF',
          700: '#B3C7FF',
          800: '#C6CCDA',
          900: '#E3E7F0',
          950: '#F4F6FB',  // was near-black headings → light ink
        },
        accent: {
          50: '#171A22',   // was a lavender tint    → raised plane
          100: '#10141C',
          200: '#1C212B',
          300: '#8AA6FF',
          400: '#6385FF',
          500: '#2F6BFF',  // was the purple accent  → electric blue
          600: '#2557DB',
          700: '#1C43AE',
          800: '#B3C7FF',
          900: '#D7E2FF',
          950: '#EEF3FF',
        },
        surface: {
          50: '#0F1117',   // was the page canvas    → recessed plane
          100: '#171A22',  // was a chip / skeleton  → raised plane
          200: '#20252F',
          300: '#2B313D',  // was a light border     → hairline on midnight
          400: '#7C8595',  // muted meta
          500: '#A2AAB9',  // body copy
          600: '#C6CCDA',
          700: '#E3E7F0',
          800: '#F4F6FB',
          900: '#F4F6FB',
          950: '#F4F6FB',
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
        'heading-md': ['clamp(1.125rem, 2vw, 1.5rem)', { lineHeight: '1.35', fontWeight: '600' }],
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
        // On midnight a drop shadow is nearly invisible, so these are mostly a
        // hairline of light along the top edge plus a soft black lift. Kept
        // under the old names so every `shadow-card` a page already writes
        // picks up the dark treatment for free.
        'card': '0 1px 0 0 rgba(255, 255, 255, 0.04), 0 10px 28px -14px rgba(0, 0, 0, 0.9)',
        'card-hover': '0 1px 0 0 rgba(255, 255, 255, 0.07), 0 18px 44px -18px rgba(0, 0, 0, 0.95)',
        'elevated': '0 24px 70px -22px rgba(0, 0, 0, 0.95)',
        // These three were rgba(102, 126, 234) — violet. Now electric blue.
        'glow': '0 6px 22px -6px rgba(47, 107, 255, 0.55)',
        'glow-orange': '0 6px 22px -6px rgba(255, 122, 26, 0.45)',
        'focus': '0 0 0 3px rgba(47, 107, 255, 0.38)',
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
