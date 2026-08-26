import { KnowledgeService } from './knowledge.service';
export declare class KnowledgeController {
    private knowledge;
    constructor(knowledge: KnowledgeService);
    findAll(category?: string, q?: string): import(".prisma/client").Prisma.PrismaPromise<{
        id: string;
        createdAt: Date;
        updatedAt: Date;
        title: string;
        category: string;
        content: string;
        tags: string | null;
    }[]>;
    search(q: string): import(".prisma/client").Prisma.PrismaPromise<{
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
}
