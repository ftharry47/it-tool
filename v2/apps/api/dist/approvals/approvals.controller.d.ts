import { Request } from 'express';
import { ApprovalsService } from './approvals.service';
declare class DecideDto {
    status: 'APPROVED' | 'REJECTED';
    comment?: string;
}
export declare class ApprovalsController {
    private approvals;
    constructor(approvals: ApprovalsService);
    findPending(req: Request & {
        user: any;
    }): import(".prisma/client").Prisma.PrismaPromise<({
        issue: {
            requester: {
                id: string;
                email: string;
                name: string;
            };
        } & {
            id: string;
            createdAt: Date;
            updatedAt: Date;
            title: string;
            description: string;
            impact: string;
            category: string;
            type: string;
            customFields: string | null;
            ticketId: string;
            status: string;
            priority: string;
            requesterId: string;
            assigneeId: string | null;
        };
    } & {
        comment: string | null;
        id: string;
        managerId: string;
        createdAt: Date;
        updatedAt: Date;
        status: string;
        issueId: string;
    })[]>;
    decide(req: Request & {
        user: any;
    }, id: string, body: DecideDto): Promise<{
        issue: {
            id: string;
            createdAt: Date;
            updatedAt: Date;
            title: string;
            description: string;
            impact: string;
            category: string;
            type: string;
            customFields: string | null;
            ticketId: string;
            status: string;
            priority: string;
            requesterId: string;
            assigneeId: string | null;
        };
    } & {
        comment: string | null;
        id: string;
        managerId: string;
        createdAt: Date;
        updatedAt: Date;
        status: string;
        issueId: string;
    }>;
}
export {};
