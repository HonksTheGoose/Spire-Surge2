# Build the mod with GitHub (no Java needed)

1. Sign in at github.com and click **New repository**. Name it `spire-surge`, leave it empty, click **Create repository**.
2. On the new repo page click **uploading an existing file**.
3. Unzip this project and drag **everything inside the `spire-surge` folder** into the page.
   Make sure the hidden `.github` folder is included (turn on "Show hidden files" if you don't see it).
4. Click **Commit changes**.
5. Open the **Actions** tab. A run called **Build mod jar** starts by itself (about 3-6 minutes).
   If it doesn't, click **Build mod jar** on the left, then **Run workflow**.
6. When it shows a green tick, open the run and download **spire-surge-jar** from **Artifacts**. Unzip it.
7. Put the `.jar` inside it into your `mods` folder (server and clients) next to Spawn Spires.

If the run goes red, open it, copy the error text from the failed step, and send it to me.
