import {contextBridge, ipcRenderer, webUtils} from 'electron';

contextBridge.exposeInMainWorld('electronAPI', {
  getPathForFile: (file: File): string => webUtils.getPathForFile(file),
  readFile: (path: string): Promise<Uint8Array> => ipcRenderer.invoke('read-file', path),
  onOpenFilePath: (cb: (path: string) => void) => {
    ipcRenderer.on('open-file-path', (_event, path: string) => cb(path));
  },
});
