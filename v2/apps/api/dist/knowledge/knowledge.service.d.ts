import { PrismaService } from '../prisma/prisma.service';
export declare class KnowledgeService {
    private prisma;
    constructor(prisma: PrismaService);
    findAll(category?: string, q?: string): import(".prisma/client").Prisma.PrismaPromise<{
        id: string;
        createdAt: Date;
        updatedAt: Date;
        title: string;
        category: string;
        content: string;
        tags: string | null;
    }[]>;
    findOne(id: string): import(".prisma/client").Prisma.Prisma__KnowledgeArticleClient<{
        id: string;
        createdAt: Date;
        updatedAt: Date;
        title: string;
        category: string;
        content: string;
        tags: string | null;
    } | null, null, import("@prisma/client/runtime/library").DefaultArgs>;
    search(q: string): import(".prisma/client").Prisma.PrismaPromise<{
        id: string;
        createdAt: Date;
        updatedAt: Date;
        title: string;
        category: string;
        content: string;
        tags: string | null;
    }[]>;
}
