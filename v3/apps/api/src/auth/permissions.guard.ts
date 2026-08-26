import { Injectable, CanActivate, ExecutionContext } from '@nestjs/common'
import { Reflector } from '@nestjs/core'

export const PERMISSIONS_KEY = 'permissions'

@Injectable()
export class PermissionsGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const required = this.reflector.getAllAndOverride<string[]>(PERMISSIONS_KEY, [
      context.getHandler(),
      context.getClass(),
    ])
    if (!required || required.length === 0) return true

    const { user } = context.switchToHttp().getRequest()
    if (!user || !user.permissions) return false
    if (user.permissions.includes('admin:all')) return true

    return required.every((permission) => user.permissions.includes(permission))
  }
}
