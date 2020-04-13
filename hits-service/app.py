import logging
from jaeger_client import Config
from opentracing.ext import tags
from opentracing.propagation import Format
from flask import Flask, request
import redis
import os

def init_tracer(service):
    logging.getLogger('').handlers = []
    logging.basicConfig(format='%(message)s', level=logging.DEBUG)

    config = Config(
        config={ # usually read from some yaml config
            'sampler': {
                'type': 'const',
                'param': 1,
            },
            'logging': True,
            'reporter_batch_size': 1,
        },
        service_name=service,
    )

    # this call also sets opentracing.tracer
    return config.initialize_tracer()


r = redis.Redis(decode_responses=True, 
                password=os.environ.get("DB_PASSWORD", "pass"), 
                host=os.environ.get("DB_HOST", "localhost"))

app = Flask(__name__)
tracer = init_tracer('Hit Service')


@app.route("/health")
def health():
    return {"status": "0", "message": "OK"} 

@app.route("/api/hits/<id>")
def get(id):
    try :
        span_ctx = tracer.extract(Format.HTTP_HEADERS, request.headers)
        span_tags = {tags.SPAN_KIND: tags.SPAN_KIND_RPC_SERVER, "id_song": id}
        with tracer.start_span('hits', child_of=span_ctx, tags=span_tags) as span:
                with tracer.start_span('redis', child_of=span) as redis_span:
                    hits = r.get(id) if r.get(id) is not None else 0
                    total = r.get("total") if r.get("total") is not None else 0

        return {"status": "0", "hits": str(hits), "total": str(total)}
    except redis.ConnectionError as e:
        return {"status": "-1", "message": str(e)}
    except:
        return {"status": "-1", "message": "Unexpected Error"} 
    
@app.route("/api/popularity/<id>")
def get_popularity(id):

    try: 
        span_ctx = tracer.extract(Format.HTTP_HEADERS, request.headers)
        span_tags = {tags.SPAN_KIND: tags.SPAN_KIND_RPC_SERVER, "id_song": id}
        with tracer.start_span('popularity', child_of=span_ctx, tags=span_tags) as span:
            with tracer.start_span('redis', child_of=span) as redis_span:
                print(id)
                hits = int(r.get(id)) if r.get(id) is not None else 0
                total = int(r.get("total")) if r.get("total") is not None else 0
                
        if (total is not 0):
            ratio = hits * 5 // total
        else:
            ratio = 0 

        return {"status": "0", "popularity": str(round(ratio, 2))}
    except redis.ConnectionError as e:
        return {"status": "-1", "message": str(e)}
    except Exception as e:
        print(e)
        return {"status": "-1", "message": "Unexpected Error"}    

@app.route("/api/hits", methods=["POST"])
def hit():

    try:
        span_ctx = tracer.extract(Format.HTTP_HEADERS, request.headers)
        span_tags = {tags.SPAN_KIND: tags.SPAN_KIND_RPC_SERVER}
        with tracer.start_span('hits', child_of=span_ctx, tags=span_tags) as span:
            data = request.json
            id = int(data["id"])
            with tracer.start_span('redis', child_of=span) as redis_span:
                r.setnx(id, 0)  
                r.incr(id, 1)

                r.setnx("total", 0)
                r.incr("total", 1)

                hits = int(r.get(id))
                total = int(r.get("total"))

            ratio = hits * 5 / total

            return {"status": "0", "popularity": str(round(ratio, 2))}
    except redis.ConnectionError as e:
        return {"status": "-1", "message": str(e)}
    except Exception as e:
        print(e)
        return {"status": "-1", "message": "Unexpected Error"} 

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)

