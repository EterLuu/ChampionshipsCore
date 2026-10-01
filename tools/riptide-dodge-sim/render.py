#!/usr/bin/env python3
"""Create a standalone, scrubbable replay from the simulator's JSON traces."""
import json
import sys
from pathlib import Path

data = json.loads(Path(sys.argv[1]).read_text())
template = Path(__file__).with_name("replay.html").read_text()
Path(sys.argv[2]).write_text(template.replace("/* REPLAY_DATA */", json.dumps(data, separators=(",", ":"))))
