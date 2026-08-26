import { Request } from 'express';
import { AuthService } from './auth.service';
declare class LoginDto {
    email: string;
}
export declare class AuthController {
    private auth;
    constructor(auth: AuthService);
    login(body: LoginDto): Promise<{
        accessToken: string;
        user: import("./auth.service").AuthenticatedUser;
    }>;
    me(req: Request & {
        user: any;
    }): any;
}
export {};
