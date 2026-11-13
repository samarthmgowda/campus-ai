# Campus Connect Landing Page — Design Specification

**Color Theme:** Midnight Black (60%) + Electric Blue (30%) + Sunset Orange (10%)
- Midnight Black: `#090A10` — dominant background
- Electric Blue: `#2F6BFF` — secondary interactive elements
- Sunset Orange: `#FF7A1A` — primary CTA accent only

**Typeface:** Geist (never Inter, Roboto, Arial, Open Sans, Helvetica)
- No italic fonts anywhere
- No ultra bold weights (900/black); cap at semibold or bold

**Layout Type:** A — Classic hero plus sections (most appropriate: product understandable from hero screenshot)

## Page Structure (per Skill Part A2)

### Above the Fold (Required)
1. Headline, outcome plus audience
2. Subheadline, clarifies how and adds specificity
3. Primary CTA, clear verb plus what they get
4. One proof signal, logo strip, stat, or short testimonial
5. Hero visual, product screenshot or strong illustration

### Mid Page (The Argument)
6. Problem to solution, one section
7. Benefits, three to five, outcome driven
8. How it works, three steps
9. Social proof, testimonials or a case study

### Bottom (Objection Handling)
10. FAQ, six to twelve questions
11. Risk reversal, trial, cancel anytime, guarantee
12. Final CTA, identical to the top

**Mandatory tagline reveal section from B11** placed after the hero or after benefits.

### Build Order (per Skill A6)
1. Hero
2. Benefits
3. How it works
4. Proof
5. FAQ
6. Final CTA

## Hero Section (B5)
- Heading and subheading: max width 680px
- Gradient on heading text only (not background):
  - Dark theme: `#FFFFFF` → `#9B9B9B` left to right
  - Light theme: `#000000` → `#666666` left to right
- Meaningful line breaks in heading copy
- Hero visual: product screenshot or strong illustration

## Benefits (A5)
- Three to five outcome-driven benefit bullets
- Format: **Bold benefit**, then the proof or detail
- Example: **Faster iteration** — generate three layout variants in one click
- No hyphens inside sentences, no orphaned words

## How It Works (A5)
- Three steps showing the workflow
- Visual elements for each step

## Tagline Reveal (B11) — Mandatory
- Minimum two lines of text
- Benefit statement or tagline, written in A5 voice, not generic heading
- Typography: Heading 3 to Heading 1 depending on line count
- Max width capped like hero (680px)
- Animation: Each word transitions individually from ~25-35% opacity of base text color to full text color, in reading order
- Words activate one at a time as they cross a trigger line, not entire block at once
- Transition uses base curve: `ease-[cubic-bezier(0.22,1,0.36,1)]` with `duration-500`
- Under `prefers-reduced-motion`: show final text at full color right away, no word-by-word animation

## States (B9) — All Interactive Elements
- **Hover**: background shift, slight scale, or translate
- **Active**: `scale(0.98)` or `translateY(1px)` for physical feedback
- **Focus**: visible focus ring (accessibility requirement)
- **Loading**: skeleton loaders shaped like real layout, not circular spinners
- **Empty**: composed "getting started" view, never blank panel
- **Error**: inline and specific. Never `window.alert()`

## Content Realism (B8)
- Write real copy in product's voice — no placeholder Latin
- Specific names for people/testimonials, never reuse one face for two people
- Invent brand names that fit education/software industry
- Never invent facts; use marked placeholders `{{price}}` or `{{testimonial}}` when missing
- Avoid suspiciously round figures; use decimals or odd final digits
- **Banned filler words**: "revolutionize", "supercharge", "cutting edge", "effortless", "transform your workflow", "take it to the next level", "harness the power", "world class"
- Sentence case for headings and buttons

## Visual Rules (Skill Part B)
- **Single approved typeface**, no italics, no ultra bold
- **No hyphens in copy**, no orphaned words
- **Every font size** lands on Tailwind scale step
- **Every text element** uses named text style (Heading, Title, Body, Button, Caption)
- **Every spacing value** comes from spacing table
- **Nested radii** follow formula: inner = outer − gap (only when result > 2)
- **No single-sided card borders**, no background gradients
- **Hero heading/subheading** capped at 680px with meaningful line breaks
- **Icons** from Phosphor, Solar, or Iconamoon (never Material Icons/Symbols)
- **Every transition** uses B7 base curve; scroll reveals use IntersectionObserver
- **Tagline reveal** present, minimum two lines, words activate one at a time on scroll
- **No invented prices, stats, or testimonials**; missing facts use marked placeholders
- **Checked at 390px** with no horizontal overflow; buttons keep text inside; primary action visible in first screen

## Ship Requirements (B10)
- Privacy policy and terms links in footer
- Custom, branded 404 page
- Client-side form validation for email format and required fields
- Skip to content link for keyboard users
- Cookie consent where jurisdiction requires it
- Branded favicon
- `<title>`, meta description, `og:image`, and social sharing tags
- Alt text on every meaningful image
- Semantic HTML: `<nav>`, `<main>`, `<article>`, `<aside>`, `<section>`
- Way back from every page
- Check at 390px: no horizontal overflow, buttons keep text inside, primary action visible in first screen

## Output Format (Skill Output)
1. Page outline — sections and their order ✓
2. Hero copy — headline, subheadline, CTA, proof line
3. Benefits — three to five outcome driven bullets
4. How it works — three steps
5. FAQ — six to twelve questions and answers
6. SEO / AEO — index or noindex recommendation, plus title and meta if indexed
7. Layout recommendation — Type A, and why

---
## Copy Draft

### Headline
"Manage your entire campus lifecycle in one platform"

### Subheadline
"From attendance to placements — AI-powered insights that help students succeed and administrators lead"

### Primary CTA
"Get Started Free — No credit card required"

### Benefit Bullets
- **Track attendance automatically** — AI identifies faces, reduces manual entry by 90%
- **Verify skills with evidence** — AI scores proof, mentors verify, only verified skills reach recruiters
- **Match candidates to drives** — Ranked eligibility engine connects verified students to opportunities
- **Build ATS-ready resumes** — Auto-generated from verified skills and projects

### How It Works Steps
1. **Upload** — Drop files, notes, or lecture slides
2. **AI Process** — Artificial intelligence extracts insights, generates quizzes and flashcards
3. **Results** — View analytics, ranked candidates, and download resumes

### Tagline Reveal (after hero)
"Verified data. Better outcomes. Automated campus management."

## SEO
- Title: "Campus Connect — AI-Powered Campus Lifecycle Management"
- Description: "Manage attendance, verify skills, match candidates to drives, and build resumes automatically. The all-in-one platform for campus lifecycle management."
- Index: Evergreen offer — do index
- FAQ schema markup where appropriate