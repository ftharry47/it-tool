import * as React from 'react'
import { cn } from '@/lib/utils'

export interface SwitchProps extends React.InputHTMLAttributes<HTMLInputElement> {}

const Switch = React.forwardRef<HTMLInputElement, SwitchProps>(({ className, ...props }, ref) => {
  return (
    <input
      type="checkbox"
      className={cn(
        'peer h-5 w-9 appearance-none rounded-full bg-input transition-colors checked:bg-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
        className
      )}
      ref={ref}
      {...props}
    />
  )
})
Switch.displayName = 'Switch'

export { Switch }
