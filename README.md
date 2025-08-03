Akka benchmark for comparison with coroio (https://github.com/resetius/coroio)

Results:
Local ring (100 actors, 1024 batch size):
Akka: 26931469,44 msg/s
Coroio: 6.11893e+07 msg/s

Non-local ring (10 ators, 1024 batch size)::
Akka: 3626,99 msg/s
Coroio: 357996 msg/s

