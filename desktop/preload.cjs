const { contextBridge } = require('electron');
const { spawnSync } = require('node:child_process');

contextBridge.exposeInMainWorld('codereviewx', {
  saveCredentials(config) {
    const value = JSON.stringify(config);
    spawnSync('security', ['add-generic-password', '-s', 'CodeReviewX.credentials', '-a', 'local', '-w', value, '-U'], { stdio: 'ignore' });
  },
  clearCredentials() {
    spawnSync('security', ['delete-generic-password', '-s', 'CodeReviewX.credentials'], { stdio: 'ignore' });
  },
  restart() {
    require('node:electron').app.relaunch();
    require('node:electron').app.exit(0);
  }
});
