# Scripts

The project keeps small `.bat` launchers in the repository root so a Windows user can run the main scenarios from the project folder without typing a long PowerShell command.

Actual implementation lives here:

- `scenario-reset.ps1` resets the coursework scenario to 30 store addresses and 30 approved orders.
- `scenario-clear-orders.ps1` clears order data while keeping store addresses.

Usage examples:

```powershell
.\scenario-reset.ps1 -Base http://127.0.0.1:8080/api
.\scenario-reset.ps1 -Base https://farm-sales-backend.onrender.com/api
.\scenario-clear-orders.ps1 -Base http://127.0.0.1:8080/api
.\scenario-clear-orders.ps1 -Base https://farm-sales-backend.onrender.com/api
```
