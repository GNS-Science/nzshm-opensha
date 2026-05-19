export {};

declare global {
  interface Window {
    electronAPI: {
      getPathForFile(file: File): string;
      readFile(path: string): Promise<Uint8Array>;
      onOpenFilePath(cb: (path: string) => void): void;
    };
  }
}
