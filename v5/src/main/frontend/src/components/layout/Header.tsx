import { GlobalSearch } from '../search/GlobalSearch'
import { NotificationBell } from './NotificationBell'
import { UserMenu } from './UserMenu'

export function Header() {
  return (
    <header className="sticky top-0 z-30 flex items-center justify-between gap-4 border-b border-border bg-card px-6 py-3 shadow-sm">
      <div className="flex flex-1 items-center">
        <GlobalSearch />
      </div>
      <div className="flex items-center gap-3">
        <NotificationBell />
        <UserMenu />
      </div>
    </header>
  )
}
