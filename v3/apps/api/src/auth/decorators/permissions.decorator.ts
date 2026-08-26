import { SetMetadata } from '@nestjs/common'
import { PERMISSIONS_KEY } from '../permissions.guard'

export const Permissions = (...permissions: string[]) => SetMetadata(PERMISSIONS_KEY, permissions)
