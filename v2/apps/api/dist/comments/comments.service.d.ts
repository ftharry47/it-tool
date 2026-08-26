import { PrismaService } from '../prisma/prisma.service';
export declare class CommentsService {
    private prisma;
    constructor(prisma: PrismaService);
    create(issueId: string, authorId: string, content: string): import(".prisma/client").Prisma.Prisma__CommentClient<{
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
}
