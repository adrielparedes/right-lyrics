const express = require("express");
const fs = require('fs');
const mongoose = require('mongoose');
const cors = require('cors')

const app = express();

app.use(cors())

const mongoDB = "mongodb://" + process.env.DB_USERNAME + ":" + process.env.DB_PASSWORD +  "@" + process.env.DB_HOST + "/" + process.env.DB_NAME;

mongoose.connect(mongoDB, { useNewUrlParser: true });

var db = mongoose.connection;

db.on('error', console.error.bind(console, 'MongoDB connection error:'));

var Schema = mongoose.Schema;

var Lyric = new Schema({
    name: String,
    lyric: String
});

var Lyric = mongoose.model('Lyric', Lyric);

app.get('/api/lyric/:lyricId', (req, res) => {
    Lyric.findOne({ '_id': req.params.lyricId }, 'name lyric', function (err, lyric) {
        console.log(err)
        if (err) return handleError(err);
        
        console.log(lyric)
        return res.send(lyric);
    })    
});

app.get('/api/populate/:name', (req, res) => {
    var lyricFile = fs.readFileSync('./lyrics/' + req.params.name);

    var lyric = new Lyric({
        name: req.params.name,
        lyric: lyricFile.toString()
    });

    lyric.save(function (err) {
        if (err) return handleError(err);

        return res.send({ 'id': lyric._id });
    });
});

app.listen("8080", () => {
    console.log("Listening to requests...");
});