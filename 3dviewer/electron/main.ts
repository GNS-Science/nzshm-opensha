import {app, BrowserWindow, ipcMain, dialog, Menu} from 'electron';
import {join} from 'path';
import {promises as fs} from 'fs';

function createWindow() {
  const win = new BrowserWindow({
    width: 1400,
    height: 900,
    webPreferences: {
      preload: join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
    backgroundColor: '#111111',
    title: '3D GeoJSON Viewer',
  });

  if (process.env['VITE_DEV_SERVER_URL']) {
    win.loadURL(process.env['VITE_DEV_SERVER_URL']);
  } else {
    win.loadFile(join(__dirname, '../dist/index.html'));
  }

  const menu = Menu.buildFromTemplate([
    {
      label: 'File',
      submenu: [
        {
          label: 'Open…',
          accelerator: 'CmdOrCtrl+O',
          click: async () => {
            const result = await dialog.showOpenDialog(win, {
              filters: [{name: 'GeoJSON', extensions: ['geojson', 'json']}],
              properties: ['openFile'],
            });
            if (!result.canceled && result.filePaths.length > 0) {
              win.webContents.send('open-file-path', result.filePaths[0]);
            }
          },
        },
        {type: 'separator'},
        {role: 'quit'},
      ],
    },
    {
      label: 'View',
      submenu: [
        {role: 'reload'},
        {role: 'toggleDevTools'},
        {type: 'separator'},
        {role: 'togglefullscreen'},
      ],
    },
  ]);
  Menu.setApplicationMenu(menu);
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

ipcMain.handle('read-file', async (_event, filePath: string) => {
  return fs.readFile(filePath);
});
