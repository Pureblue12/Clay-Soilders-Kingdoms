// Headless screenshots: starts the studio server in-process, opens render.html in headless Chromium (Playwright)
// and writes one PNG per camera view.
import fs from 'node:fs';
import path from 'node:path';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { createServer, type ViteDevServer } from 'vite';
import { chromium, type Browser } from 'playwright';
import { STUDIO_ROOT } from '../src/server/store.ts';
import type { StructureFile } from '../src/format/structure.ts';

export interface RenderOptions {
  views: string[];
  width: number;
  height: number;
  terrain: string;
  size?: number;
  trees?: boolean;
  layer?: string;
  y?: number;
  markers?: boolean;
}

export interface RenderJob {
  structure: StructureFile;
  outDir: string;
  /** file name prefix, e.g. "keep_v2" */
  prefix: string;
}

let scratch: StructureFile | null = null;

async function startServer(): Promise<{ server: ViteDevServer; url: string }> {
  const server = await createServer({
    root: STUDIO_ROOT,
    configFile: path.join(STUDIO_ROOT, 'vite.config.ts'),
    logLevel: 'error',
    server: { port: 0, host: '127.0.0.1', hmr: false },
    plugins: [
      {
        name: 'studio-scratch',
        configureServer(s) {
          s.middlewares.use((req: IncomingMessage, res: ServerResponse, next: () => void) => {
            if (req.url?.startsWith('/__scratch') && scratch) {
              res.setHeader('Content-Type', 'application/json');
              res.end(JSON.stringify(scratch));
              return;
            }
            next();
          });
        },
      },
    ],
  });
  await server.listen();
  const addr = server.httpServer!.address();
  const port = typeof addr === 'object' && addr ? addr.port : 5174;
  return { server, url: `http://127.0.0.1:${port}` };
}

export async function launchBrowser(): Promise<Browser> {
  const args = ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--enable-webgl'];
  const exe = process.env.STUDIO_CHROMIUM;
  return chromium.launch({ headless: true, args, ...(exe ? { executablePath: exe } : {}) });
}

/** Renders every job; returns the written file paths. */
export async function renderStructures(jobs: RenderJob[], opts: RenderOptions): Promise<string[]> {
  const { server, url } = await startServer();
  let browser: Browser | null = null;
  const written: string[] = [];
  try {
    browser = await launchBrowser();
    const page = await browser.newPage({ viewport: { width: opts.width, height: opts.height } });
    page.on('console', (m) => {
      if (m.type() === 'error') console.error('[page]', m.text());
    });
    for (const job of jobs) {
      scratch = job.structure;
      const q = new URLSearchParams({
        src: `/__scratch?t=${Date.now()}`,
        views: opts.views.join(','),
        w: String(opts.width),
        h: String(opts.height),
        terrain: opts.terrain,
        ...(opts.size ? { size: String(opts.size) } : {}),
        ...(opts.trees ? { trees: '1' } : {}),
        ...(opts.layer ? { layer: opts.layer, y: String(opts.y ?? 0) } : {}),
        ...(opts.markers ? { markers: '1' } : {}),
      });
      await page.goto(`${url}/render.html?${q}`);
      await page.waitForFunction(() => window.__result || window.__error, null, { timeout: 180_000, polling: 250 });
      const err = await page.evaluate(() => window.__error);
      if (err) throw new Error(`render failed for ${job.prefix}: ${err}`);
      const result = (await page.evaluate(() => window.__result)) as { views: Record<string, string>; placeholders: number; assets: string };
      fs.mkdirSync(job.outDir, { recursive: true });
      for (const [view, dataUrl] of Object.entries(result.views)) {
        const file = path.join(job.outDir, `${job.prefix}_${view}.png`);
        fs.writeFileSync(file, Buffer.from(dataUrl.split(',')[1], 'base64'));
        written.push(file);
      }
      if (result.assets === 'none') console.warn('note: no Minecraft assets found, the renders use placeholder textures');
      else if (result.placeholders) console.warn(`note: ${result.placeholders} block state(s) had no model and use placeholders`);
    }
  } finally {
    await browser?.close();
    await server.close();
  }
  return written;
}
