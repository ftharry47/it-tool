import { NestFactory } from '@nestjs/core'
import { NestExpressApplication } from '@nestjs/platform-express'
import { ExpressAdapter } from '@nestjs/platform-express'
import { AppModule } from './app.module'

async function bootstrap() {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, new ExpressAdapter())
  app.setGlobalPrefix('api')
  app.enableCors({
    origin: true,
    credentials: true,
  })

  const port = Number(process.env.API_PORT) || 3003
  console.log(`[bootstrap] about to listen on port ${port}`)
  const server = await app.listen(port, '0.0.0.0')
  console.log(`API running on http://localhost:${port}/api, server listening=${server.listening}`)
}

bootstrap()
