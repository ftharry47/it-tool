import { JwtService } from '@nestjs/jwt';
import { User } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
export type AuthenticatedUser = {
    id: string;
    email: string;
    name: string;
    role: string;
    managerId: string | null;
};
export declare class AuthService {
    private jwt;
    private prisma;
    constructor(jwt: JwtService, prisma: PrismaService);
    validateUser(email: string): Promise<AuthenticatedUser | null>;
    login(email: string): Promise<{
        accessToken: string;
        user: AuthenticatedUser;
    }>;
    toUser(user: User): AuthenticatedUser;
}
