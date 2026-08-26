import { PrismaService } from '../prisma/prisma.service';
export declare class ApprovalsService {
    private prisma;
    constructor(prisma: PrismaService);
    findPendingForManager(managerId: string): import(".prisma/client").Prisma.PrismaPromise<({
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
    decide(id: string, managerId: string, status: 'APPROVED' | 'REJECTED', comment?: string): Promise<{
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
