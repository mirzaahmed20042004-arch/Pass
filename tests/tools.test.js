import test from 'node:test';
import assert from 'node:assert/strict';
import {tools,runTool} from '../src/tools.js';
test('catalogue has 20 unique tools with two online',()=>{assert.equal(tools.length,20);assert.equal(new Set(tools.map(x=>x.id)).size,20);assert.equal(tools.filter(x=>x.online).length,2)});
test('local tools process input',async()=>{assert.equal(await runTool('grammar','i  recieve teh package .'),'I receive the package.');assert.match(await runTool('wordcount','Hello world.'),/Words: 2/);assert.match(await runTool('points','One sentence. Another sentence.'),/•/);assert.equal(await runTool('case','hello','title'),'Hello');assert.equal(await runTool('base64',await runTool('base64','Hello 🌱'),'decode'),'Hello 🌱')});
test('passwords use requested length and validate it',async()=>{assert.equal((await runTool('password','24')).length,24);await assert.rejects(runTool('password','5'))});
test('empty inputs produce a useful error',async()=>{await assert.rejects(runTool('rewrite','  '),/Add some text/)});
