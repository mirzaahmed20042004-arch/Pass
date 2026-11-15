from __future__ import annotations
import os
import httpx
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="App Fly API", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])
TIMEOUT = httpx.Timeout(18.0, connect=8.0)
HEADERS = {"User-Agent": "AppFly/1.0 (open knowledge discovery app)"}

@app.get('/api/health')
def health():
    return {"status": "ok", "app": "App Fly"}

async def get_json(url: str, params: dict | None = None):
    async with httpx.AsyncClient(timeout=TIMEOUT, headers=HEADERS, follow_redirects=True) as client:
        response = await client.get(url, params=params)
        response.raise_for_status()
        return response.json()

@app.get('/api/search')
async def search(q: str = Query(min_length=1, max_length=240), lang: str = 'en'):
    q = q.strip()
    if not q:
        raise HTTPException(400, 'Enter a search phrase.')
    lang = lang if lang in {'en','ur','es','fr','de','it','pt','ru','ja','zh','ar'} else 'en'
    wiki_host = f'https://{lang}.wikipedia.org'
    wiki_result, books_result = await __import__('asyncio').gather(
        get_json(f'{wiki_host}/w/api.php', {'action':'query','generator':'search','gsrsearch':q,'gsrlimit':6,'gsrnamespace':0,'prop':'pageimages|extracts|info','inprop':'url','exintro':1,'explaintext':1,'exsentences':5,'piprop':'thumbnail','pithumbsize':1000,'format':'json','origin':'*'}),
        get_json('https://openlibrary.org/search.json', {'q':q,'limit':8,'fields':'key,title,author_name,first_publish_year,cover_i,edition_count,subject'}),
        return_exceptions=True)
    wiki = []
    if not isinstance(wiki_result, Exception):
        pages = wiki_result.get('query', {}).get('pages', {})
        wiki = [{"title":p.get('title',''), "extract":p.get('extract',''), "image":p.get('thumbnail',{}).get('source'), "url":p.get('fullurl') or f"https://{lang}.wikipedia.org/wiki/{p.get('title','').replace(' ','_')}"} for p in pages.values()]
    books = []
    if not isinstance(books_result, Exception):
        for item in books_result.get('docs', []):
            cover = item.get('cover_i')
            books.append({"title":item.get('title','Untitled'), "authors":item.get('author_name',[])[:3], "year":item.get('first_publish_year'), "editions":item.get('edition_count',0), "cover":f"https://covers.openlibrary.org/b/id/{cover}-L.jpg" if cover else None, "url":f"https://openlibrary.org{item.get('key','')}"})
    if not wiki and not books:
        raise HTTPException(502, 'Could not reach the open knowledge sources. Please try again in a moment.')
    return {"query":q, "wikipedia":wiki, "books":books,
            "notice":"Results are fetched from Wikipedia and Open Library. Open full pages only when you choose a source link."}

@app.get('/api/translate')
async def translate(q: str = Query(min_length=1, max_length=5000), source: str='auto', target: str='ur'):
    endpoint = os.getenv('LIBRETRANSLATE_URL', 'https://translate.argosopentech.com/translate').rstrip('/')
    api_key = os.getenv('LIBRETRANSLATE_API_KEY', '')
    payload = {"q": q, "source": source, "target": target, "format": "text"}
    if api_key: payload['api_key'] = api_key
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT, headers=HEADERS) as client:
            response = await client.post(endpoint, json=payload)
            response.raise_for_status()
            result = response.json()
        return {"translatedText":result.get('translatedText',''), "source":source, "target":target, "provider":"LibreTranslate"}
    except Exception as exc:
        raise HTTPException(502, 'Translation is temporarily unavailable. Configure LIBRETRANSLATE_URL with a LibreTranslate instance and try again.') from exc
