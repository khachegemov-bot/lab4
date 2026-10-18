# =====================================================================
# Автоматическая настройка БД для лабораторной (Windows PowerShell).
# Запуск из корня проекта:
#   powershell -ExecutionPolicy Bypass -File db\setup.ps1
#
# Что делает:
#   1) находит psql;
#   2) спрашивает пароль суперпользователя postgres (он НИГДЕ не сохраняется);
#   3) генерирует случайные пароли для пользователей city_app и city_readonly;
#   4) создаёт роли, базу city_reports, таблицы и тестовые данные;
#   5) записывает файл .env (он в .gitignore и в git не попадает).
# ВНИМАНИЕ: повторный запуск пересоздаёт таблицы — данные будут удалены.
# =====================================================================
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

# --- 1. psql ---
$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $psql) { throw 'psql не найден. Установите PostgreSQL (вместе с Command Line Tools).' }
Write-Host "psql: $psql"

# --- 2. пароль суперпользователя (только в памяти этого процесса) ---
$secure = Read-Host 'Пароль пользователя postgres (задавали при установке)' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
$env:PGCLIENTENCODING = 'UTF8'

# --- 3. случайные пароли: 24 символа, криптостойкий генератор ---
function New-RandomPassword {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}
$appPassword = New-RandomPassword
$readonlyPassword = New-RandomPassword

function Invoke-Psql([string]$database, [string]$file, [string[]]$extra = @()) {
    & $psql -U postgres -h localhost -d $database -v ON_ERROR_STOP=1 @extra -f (Join-Path $PSScriptRoot $file)
    if ($LASTEXITCODE -ne 0) { throw "Ошибка при выполнении $file" }
}

try {
    # --- 4. роли, база, схема, данные ---
    Invoke-Psql 'postgres'     '01_create_db_and_roles.sql' @('-v', "app_password=$appPassword", '-v', "readonly_password=$readonlyPassword")
    Invoke-Psql 'city_reports' '02_schema.sql'
    Invoke-Psql 'city_reports' '03_seed.sql'
}
finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

# --- 5. .env (UTF-8 без BOM, иначе Spring не прочитает первую строку) ---
$envText = "DB_URL=jdbc:postgresql://localhost:5432/city_reports`nDB_USERNAME=city_app`nDB_PASSWORD=$appPassword`n" +
           "# пользователь только для чтения (отчёты, pgAdmin); приложение его не использует`n" +
           "READONLY_DB_USERNAME=city_readonly`nREADONLY_DB_PASSWORD=$readonlyPassword`n"
[IO.File]::WriteAllText((Join-Path $projectRoot '.env'), $envText, (New-Object Text.UTF8Encoding($false)))

Write-Host ''
Write-Host 'Готово: база city_reports создана, тестовые данные загружены, файл .env записан.' -ForegroundColor Green
