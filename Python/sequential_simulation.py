import random
import config as C

def run_once(rng: random.Random) -> float:
    server_free_at = [0.0] * C.NUM_SERVERS
    current_time   = 0.0
    total_wait     = 0.0

    for _ in range(C.NUM_PATIENTS):
        current_time += rng.expovariate(C.LAMBDA)
        best = min(range(C.NUM_SERVERS), key=lambda s: server_free_at[s])
        wait = max(0.0, server_free_at[best] - current_time)
        total_wait += wait
        service_start = max(current_time, server_free_at[best])
        server_free_at[best] = service_start + rng.expovariate(C.MU)

    return total_wait / C.NUM_PATIENTS
