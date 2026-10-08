import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import { cn } from '@/lib/cn'

export function Modal({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-navy/40 p-4 animate-fade-in">
      <Card className={cn('w-full max-w-sm animate-scale-in', className)}>{children}</Card>
    </div>
  )
}
