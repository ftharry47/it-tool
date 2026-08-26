import * as React from 'react'
import { cn } from '@/lib/utils'

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  loading?: boolean
  variant?: 'default' | 'ghost'
  size?: 'default' | 'sm' | 'icon'
}

export function Button({ children, className, loading, variant = 'default', size = 'default', ...props }: ButtonProps) {
  return (
    <button
      disabled={loading || props.disabled}
      className={cn(
        'inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors disabled:opacity-50',
        size === 'default' && 'px-4 py-2',
        size === 'sm' && 'px-2 py-1 text-xs',
        size === 'icon' && 'h-8 w-8 p-1',
        variant === 'default' && 'bg-primary text-primary-foreground hover:bg-primary/90',
        variant === 'ghost' && 'hover:bg-accent hover:text-accent-foreground',
        className
      )}
      {...props}
    >
      {loading && (
        <span className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      )}
      {children}
    </button>
  )
}
