"""Run the GUI's mob classification helper and attribute controls in a real ServerLevel."""
from pathlib import Path
import argparse
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--loader', choices=['fabric', 'forge', 'neoforge'], required=True)
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    run = ROOT / f'build/mob-list-run-{args.loader}'
    run.mkdir(parents=True, exist_ok=True)
    (run / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    port = 25630 + (2 if ROOT.name.endswith('1.21.1') else 0) + (args.loader != 'fabric')
    properties = {
        'level-name': 'mob-list-test-world', 'level-seed': '87654',
        'level-type': 'minecraft:flat', 'generate-structures': 'false',
        'generator-settings': json.dumps({'layers': [{'block': 'minecraft:bedrock', 'height': 1},
                                                     {'block': 'minecraft:stone', 'height': 3}],
                                          'biome': 'minecraft:plains'}),
        'online-mode': 'false', 'server-ip': '127.0.0.1', 'server-port': str(port),
        'view-distance': '2', 'simulation-distance': '2', 'spawn-protection': '0',
        'max-tick-time': '-1', 'enable-rcon': 'false', 'difficulty': 'normal',
        'sync-chunk-writes': 'false'}
    (run / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()), encoding='utf-8')
    destination = Path(__file__).parent / 'results'
    destination.mkdir(exist_ok=True)
    report = run / 'mob-list-results.json'
    report.unlink(missing_ok=True)
    command = [str(ROOT / 'gradlew.bat'), '-I', 'verification/mob-list/verify.init.gradle',
               f'-Dmsc.verify.loader={args.loader}', f':{args.loader}:runServer', '--console=plain']
    if args.offline:
        command.append('--offline')
    log_path = destination / f'{args.loader}.log'
    print(f'Starting mob-list regression: {ROOT.name} {args.loader}', flush=True)
    with log_path.open('w', encoding='utf-8') as log:
        process = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=900)
    if report.exists():
        data = json.loads(report.read_text(encoding='utf-8'))
        (destination / f'{args.loader}.json').write_text(json.dumps(data, indent=2), encoding='utf-8')
        print(f'Passed={data["passed"]}; {len(data["checks"])} checks; {destination / (args.loader + ".json")}', flush=True)
        if data['passed'] and process.returncode == 0:
            return
    raise SystemExit(f'Failed: inspect {log_path}')


if __name__ == '__main__':
    main()
