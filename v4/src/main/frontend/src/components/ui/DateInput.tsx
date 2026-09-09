import { forwardRef, type InputHTMLAttributes } from 'react'
import { cn } from '../../lib/utils'

export interface DateInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {}

export const DateInput = forwardRef<HTMLInputElement, DateInputProps>(
  ({ className, ...props }, ref) => {
    return (
      <input
        ref={ref}
        type="date"
        className={cn(
          'w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-ring [color-scheme:light] dark:[color-scheme:dark] disabled:opacity-60 disabled:cursor-not-allowed',
          className
        )}
        {...props}
      />
    )
  }
)
DateInput.displayName = 'DateInput'
