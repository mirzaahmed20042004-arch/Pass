// Easy AI's local tools are deliberately deterministic, not an LLM.
export const tools = [
 {id:'summarize',name:'Text Summarizer',desc:'Get to the point, faster.',category:'Writing',icon:'AlignLeft',color:'mint',featured:true,input:'Paste an article or long text here...'},
 {id:'rewrite',name:'Rewrite Assistant',desc:'Clean up your writing.',category:'Writing',icon:'PenLine',color:'purple',featured:true,input:'Write or paste something to clean up...'},
 {id:'ideas',name:'Idea Generator',desc:'Find a fresh angle.',category:'Create',icon:'Lightbulb',color:'yellow',featured:true,input:'Enter a topic, e.g. sustainable living'},
 {id:'email',name:'Email Composer',desc:'Turn notes into an email.',category:'Writing',icon:'Mail',color:'blue',featured:true,input:'What is your email about?'},
 {id:'keywords',name:'Keyword Extractor',desc:'Find the words that matter.',category:'Writing',icon:'KeyRound',color:'orange'},
 {id:'points',name:'Key Points',desc:'Pull out important sentences.',category:'Writing',icon:'ListChecks',color:'mint'},
 {id:'grammar',name:'Writing Cleanup',desc:'Fix common spacing and typos.',category:'Writing',icon:'SpellCheck',color:'purple'},
 {id:'tone',name:'Tone Checker',desc:'Understand the feel of your text.',category:'Writing',icon:'ScanText',color:'blue'},
 {id:'titles',name:'Title Generator',desc:'Make a headline from a topic.',category:'Create',icon:'Heading',color:'yellow'},
 {id:'hashtags',name:'Hashtag Maker',desc:'Make tags from your keywords.',category:'Create',icon:'Hash',color:'orange'},
 {id:'format',name:'Paragraph Formatter',desc:'Give messy text some structure.',category:'Writing',icon:'Text',color:'purple'},
 {id:'case',name:'Case Converter',desc:'Change the case of your text.',category:'Everyday',icon:'CaseSensitive',color:'blue'},
 {id:'wordcount',name:'Word Counter',desc:'Count words, characters and more.',category:'Everyday',icon:'Calculator',color:'mint'},
 {id:'reading',name:'Reading Time',desc:'Know how long a read will take.',category:'Everyday',icon:'Timer',color:'yellow'},
 {id:'speech',name:'Text to Speech',desc:'Listen to your words out loud.',category:'Everyday',icon:'Volume2',color:'purple'},
 {id:'password',name:'Password Generator',desc:'Create a strong random password.',category:'Everyday',icon:'LockKeyhole',color:'orange',input:'Optional: enter a length from 12 to 64'},
 {id:'strength',name:'Password Checker',desc:'Check password strength locally.',category:'Everyday',icon:'ShieldCheck',color:'mint',input:'Enter a password to check (stays on device)'},
 {id:'base64',name:'Base64 Converter',desc:'Encode and decode text.',category:'Everyday',icon:'Binary',color:'blue'},
 {id:'wiki',name:'Wikipedia Search',desc:'Look up a topic on the web.',category:'Online',icon:'Globe2',color:'yellow',online:true,input:'Enter a topic to look up'},
 {id:'dictionary',name:'Dictionary',desc:'Find a definition online.',category:'Online',icon:'BookOpen',color:'purple',online:true,input:'Enter an English word'}
];
const stop = new Set('a an and are as at be been but by can could did do for from had has have how i in is it its of on or our that the their them there these they this to was we were what when where which who will with you your'.split(' '));
const words = s => (s.toLowerCase().match(/[\p{L}\p{N}']+/gu)||[]);
const sentences = s => (s.match(/[^.!?\n]+(?:[.!?]+|$)/g)||[]).map(x=>x.trim()).filter(Boolean);
const clean = s => s.trim().replace(/[ \t]+/g,' ').replace(/\s+([,.!?;:])/g,'$1').replace(/\bi\b/g,'I').replace(/\bteh\b/gi,'the').replace(/\brecieve\b/gi,'receive');
const keys = s => {let counts={}; words(s).filter(w=>w.length>2&&!stop.has(w)).forEach(w=>counts[w]=(counts[w]||0)+1);return Object.keys(counts).sort((a,b)=>counts[b]-counts[a]||a.localeCompare(b)).slice(0,10)};
function rank(s,n=3){const ss=sentences(s);const ks=keys(s);return ss.map((text,index)=>({text,index,score:words(text).reduce((a,w)=>a+(ks.includes(w)?1:0),0)})).sort((a,b)=>b.score-a.score||a.index-b.index).slice(0,n).sort((a,b)=>a.index-b.index).map(x=>x.text)}
export async function runTool(id,input,option='') {
 const s=input.trim(); if(!s && id!=='password') throw new Error('Add some text to get started.');
 switch(id){
 case 'summarize': return rank(s,3).join(' ');
 case 'points': return rank(s,5).map(x=>'• '+x).join('\n');
 case 'rewrite': case 'grammar': return clean(s);
 case 'ideas': return [`A beginner’s guide to ${s}`,`5 common misconceptions about ${s}`,`A practical challenge: try ${s} for 7 days`,`An interview with an expert on ${s}`,`The future of ${s}: what could change?`].map((x,i)=>`${i+1}. ${x}`).join('\n');
 case 'titles': return [`Everything You Need to Know About ${s}`,`A Simple Guide to ${s}`,`${s}: Where to Begin`,`The Practical Side of ${s}`,`What Makes ${s} Worth Exploring?`].join('\n');
 case 'email': return `Subject: ${s.split(/[.!?\n]/)[0].slice(0,70)}\n\nHello,\n\n${clean(s)}${/[.!?]$/.test(s)?'':'.'}\n\nPlease let me know your thoughts.\n\nBest regards`;
 case 'keywords': return keys(s).join(', ') || 'No keywords found.';
 case 'tone': {const positive=(s.match(/\b(thank|thanks|great|happy|love|appreciate|wonderful|excited)\b/gi)||[]).length;const negative=(s.match(/\b(bad|hate|angry|disappointed|terrible|problem|upset)\b/gi)||[]).length;return `Estimated tone: ${positive>negative?'positive':negative>positive?'concerned':'neutral'}\n\nThis is a simple keyword-based estimate, not an interpretation of intent.`}
 case 'hashtags': return keys(s).slice(0,8).map(x=>'#'+x.replace(/[^\p{L}\p{N}]/gu,'')).join(' ')||'No tags found.';
 case 'format': return s.split(/\n\s*\n/).map(p=>clean(p.replace(/\n/g,' '))).join('\n\n');
 case 'case': return option==='lower'?s.toLowerCase():option==='title'?s.toLowerCase().replace(/\b\w/g,c=>c.toUpperCase()):s.toUpperCase();
 case 'wordcount': return `Words: ${words(s).length}\nCharacters: ${s.length}\nCharacters without spaces: ${s.replace(/\s/g,'').length}\nSentences: ${sentences(s).length}`;
 case 'reading': {let n=words(s).length;return `${n} words\nAbout ${Math.max(1,Math.ceil(n/200))} minute(s) to read at 200 words per minute.`}
 case 'speech': return s;
 case 'password': {const n=s?Number(s):20;if(!Number.isInteger(n)||n<12||n>64) throw new Error('Choose a whole number from 12 to 64.'); const chars='ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*';let out='';while(out.length<n){let b=new Uint8Array(n*2);crypto.getRandomValues(b);for(const x of b){if(x<Math.floor(256/chars.length)*chars.length)out+=chars[x%chars.length];if(out.length===n)break}}return out}
 case 'strength': {let score=[s.length>=12,s.length>=16,/[a-z]/.test(s)&&/[A-Z]/.test(s),/\d/.test(s),/[^\w\s]/.test(s)].filter(Boolean).length;return `Estimated strength: ${score>=4?'strong':score>=3?'moderate':'weak'}\n\nUse a unique, long password. This simple check cannot detect breached or common passwords.`}
 case 'base64': try{return option==='decode'?new TextDecoder('utf-8',{fatal:true}).decode(Uint8Array.from(atob(s),c=>c.charCodeAt(0))):btoa(String.fromCharCode(...new TextEncoder().encode(s)))}catch{throw new Error('Could not convert this text. Check your Base64 input.')}
 case 'wiki': {let url=`https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${encodeURIComponent(s)}&gsrlimit=1&prop=extracts&exintro=1&explaintext=1&format=json&origin=*`;let r=await fetch(url);if(!r.ok)throw new Error('Wikipedia is unavailable right now.');let d=await r.json();let p=Object.values(d.query?.pages||{})[0];return p?`${p.title}\n\n${p.extract}\n\nSource: https://en.wikipedia.org/?curid=${p.pageid}`:'No results found. Try another search.'}
 case 'dictionary': {let r=await fetch(`https://api.dictionaryapi.dev/api/v2/entries/en/${encodeURIComponent(s)}`);if(!r.ok)throw new Error('No definition found, or the dictionary is unavailable.');let d=await r.json();return d.slice(0,1).map(x=>`${x.word}${x.phonetic?'  '+x.phonetic:''}\n\n${x.meanings.slice(0,3).map(m=>`${m.partOfSpeech}\n${m.definitions.slice(0,2).map((v,i)=>`${i+1}. ${v.definition}`).join('\n')}`).join('\n\n')}\n\nSource: dictionaryapi.dev`).join('')}
 default: throw new Error('Unknown tool.');
 }
}
