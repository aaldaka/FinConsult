import { useId } from 'react'

/**
 * The FinConsult mark: a round bowl of rising bars with a growth line running over it. It has no background and is
 * drawn in the current text colour, so it is white-ish on the dark nav (text-white/90) and royal blue on white
 * (text-royal). An empty title marks it as decoration, hidden from screen readers.
 */
export function Logo({ className, title = 'FinConsult' }: { className?: string; title?: string }) {
  const id = useId()
  const bowl = `${id}-bowl`
  const holes = `${id}-holes`

  // the nodes on the growth line, left to right
  const nodes = [
    [24, 47],
    [41, 38],
    [57, 28],
  ]

  return (
    <svg
      viewBox="0 0 100 100"
      className={className}
      fill="none"
      {...(title ? { role: 'img', 'aria-label': title } : { 'aria-hidden': true })}
    >
      <defs>
        {/* the bars only show inside the bowl: a circle, plus the tall edge on the right */}
        <clipPath id={bowl}>
          <circle cx="50" cy="60" r="32" />
          <rect x="50" y="22" width="32" height="38" />
        </clipPath>
        {/* each node is a ring: the line and the ring are cut out in its middle */}
        <mask id={holes}>
          <rect width="100" height="100" fill="white" />
          {nodes.map(([cx, cy]) => (
            <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="3" fill="black" />
          ))}
        </mask>
      </defs>

      <g clipPath={`url(#${bowl})`} fill="currentColor">
        {Array.from({ length: 9 }, (_, i) => {
          const x = 19 + i * 7.2
          const top = 72 - i * 5.4
          return <rect key={i} x={x} y={top} width="4.2" height={100 - top} rx="1.2" />
        })}
      </g>

      <g mask={`url(#${holes})`} stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="24,47 41,38 57,28 80,10" strokeWidth="3.2" />
        {nodes.map(([cx, cy]) => (
          <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="5.2" strokeWidth="3.2" fill="currentColor" />
        ))}
      </g>
    </svg>
  )
}
