import { Request } from 'express';
import { CommentsService } from './comments.service';
declare class CreateCommentDto {
    content: string;
}
export declare class CommentsController {
    private comments;
    constructor(comments: CommentsService);
    findByIssue(issueId: string): import(".prisma/client").Prisma.PrismaPromise<({
        author: {
            id: string;
            name: string;
        };
    } & {
        id: string;
        createdAt: Date;
        content: string;
        issueId: string;
        authorId: string;
    })[]>;
    create(req: Request & {
        user: any;
    }, issueId: string, body: CreateCommentDto): import(".prisma/client").Prisma.Prisma__CommentClient<{
        author: {
            id: string;
            name: string;
        };
    } & {
        id: string;
        createdAt: Date;
        content: string;
        issueId: string;
        authorId: string;
    }, never, import("@prisma/client/runtime/library").DefaultArgs>;
}
export {};
