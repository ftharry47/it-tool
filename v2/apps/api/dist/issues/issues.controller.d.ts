import { Request } from 'express';
import { IssuesService } from './issues.service';
import { CreateIssueDto } from './dto/create-issue.dto';
export declare class IssuesController {
    private issues;
    constructor(issues: IssuesService);
    findAll(req: Request & {
        user: any;
    }, mine?: string): Promise<({
        requester: {
            id: string;
            email: string;
            name: string;
        };
        comments: {
            id: string;
            createdAt: Date;
            issueId: string;
            authorId: string;
            content: string;
        }[];
    } & {
        id: string;
        ticketId: string;
        type: string;
        title: string;
        description: string;
        impact: string;
        category: string;
        status: string;
        priority: string;
        requesterId: string;
        assigneeId: string | null;
        customFields: string | null;
        createdAt: Date;
        updatedAt: Date;
    })[]>;
    findOne(id: string): Promise<{
        requester: {
            id: string;
            email: string;
            name: string;
        };
        assignee: {
            id: string;
            email: string;
            name: string;
        } | null;
        comments: ({
            author: {
                id: string;
                name: string;
            };
        } & {
            id: string;
            createdAt: Date;
            issueId: string;
            authorId: string;
            content: string;
        })[];
        approval: ({
            manager: {
                id: string;
                name: string;
            };
        } & {
            id: string;
            status: string;
            createdAt: Date;
            updatedAt: Date;
            managerId: string;
            comment: string | null;
            issueId: string;
        }) | null;
    } & {
        id: string;
        ticketId: string;
        type: string;
        title: string;
        description: string;
        impact: string;
        category: string;
        status: string;
        priority: string;
        requesterId: string;
        assigneeId: string | null;
        customFields: string | null;
        createdAt: Date;
        updatedAt: Date;
    }>;
    create(req: Request & {
        user: any;
    }, dto: CreateIssueDto): Promise<{
        requester: {
            id: string;
            email: string;
            name: string;
        };
        approval: ({
            manager: {
                id: string;
                name: string;
            };
        } & {
            id: string;
            status: string;
            createdAt: Date;
            updatedAt: Date;
            managerId: string;
            comment: string | null;
            issueId: string;
        }) | null;
    } & {
        id: string;
        ticketId: string;
        type: string;
        title: string;
        description: string;
        impact: string;
        category: string;
        status: string;
        priority: string;
        requesterId: string;
        assigneeId: string | null;
        customFields: string | null;
        createdAt: Date;
        updatedAt: Date;
    }>;
}
