# IT Support Portal

Node.js/Express app migrated from Google Apps Script. Serves a public form, dashboard, and JSON API handlers.

## Local run

```bash
npm install
npm start
```

Open `http://localhost:3000`.

## Deploy to Azure App Service

**Quick deploy script**
A ready-to-run PowerShell script is at `tools/deploy-azure.ps1`. From the project root run:

```powershell
.\tools\deploy-azure.ps1
```

It checks for `az` CLI, logs you in if needed, creates the App Service, builds a zip, and deploys it.

1. **Prepare the repo**
   - Make sure `package.json` has a `start` script (it does: `node app.js`).
   - Make sure `app.js` uses `process.env.PORT || 3000` (it does).
   - Create a `.gitignore` that excludes:
     - `node_modules/`
     - `tools/node/`
     - `app.log`, `*.log`
     - `public/uploads/*` (except `.gitkeep`)
     - `.env`, `.env.local`
     - local binaries: `node.exe`, `npm`, `npx`, `*.cmd`, `nodevars.bat`, `corepack*`
     - `.azure/`, `.vscode/`, `.idea/`

2. **Create the App Service (Azure CLI)**

```bash
az group create --name it-support-rg --location eastus
az appservice plan create --name it-support-plan --resource-group it-support-rg --sku B1 --is-linux
az webapp create --resource-group it-support-rg --plan it-support-plan --name it-support-portal --runtime "NODE|18-lts"
az webapp config appsettings set --resource-group it-support-rg --name it-support-portal --settings SCM_DO_BUILD_DURING_DEPLOYMENT=true
```

3. **Deploy from a zip**

```bash
# install dependencies and build package locally
npm install
# zip the project without dev/local files (PowerShell)
Compress-Archive -Path app.js,package.json,package-lock.json,public,src -DestinationPath deploy.zip -Force
az webapp deploy --resource-group it-support-rg --name it-support-portal --src-path deploy.zip
```

4. **Optional: GitHub Actions**
   - In Azure Portal, get publish profile for the Web App.
   - Add `AZUREAPPSERVICE_PUBLISHPROFILE` as a GitHub secret.
   - Use the "Node.js to Azure Web App" GitHub Actions starter workflow.

5. **Notes**
   - Azure sets `PORT` automatically; `app.js` already reads `process.env.PORT`.
   - `public/uploads` uses local disk. For scaling beyond one instance, move uploads to Azure Blob Storage.
   - Set any secrets (email credentials, etc.) under *Configuration > Application settings* in the Azure Portal, not in code.