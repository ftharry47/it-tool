import { Strategy } from 'passport-jwt';
import { ConfigService } from '@nestjs/config';
import { AuthService, AuthenticatedUser } from './auth.service';
declare const JwtStrategy_base: new (...args: any[]) => Strategy;
export declare class JwtStrategy extends JwtStrategy_base {
    private auth;
    constructor(config: ConfigService, auth: AuthService);
    validate(payload: AuthenticatedUser): Promise<AuthenticatedUser>;
}
export {};
